/*
 * (C) Copyright 2019 The DKPerms Project (Davide Wietlisbach & Philipp Elvin Friedhoff)
 *
 * @author Davide Wietlisbach
 * @since 28.10.19, 19:40
 * @website %web%
 *
 * %license%
 */

package net.pretronic.dkperms.minecraft;

import net.pretronic.dkperms.api.DKPerms;
import net.pretronic.dkperms.api.object.PermissionHolderFactory;
import net.pretronic.dkperms.api.object.PermissionObject;
import net.pretronic.dkperms.api.object.PermissionObjectType;
import net.pretronic.dkperms.api.scope.PermissionScope;
import net.pretronic.dkperms.api.scope.PermissionScopeManager;
import net.pretronic.dkperms.common.DefaultDKPerms;
import net.pretronic.dkperms.common.DefaultMigrationAssistant;
import net.pretronic.dkperms.common.logging.DefaultAuditLog;
import net.pretronic.dkperms.common.object.DefaultPermissionObject;
import net.pretronic.dkperms.common.object.DefaultPermissionObjectManager;
import net.pretronic.dkperms.common.permission.DefaultPermissionAnalyser;
import net.pretronic.dkperms.common.scope.DefaultPermissionScopeManager;
import net.pretronic.dkperms.common.storage.PDQStorage;
import net.pretronic.dkperms.minecraft.commands.TeamCommand;
import net.pretronic.dkperms.minecraft.commands.permission.PermissionCommand;
import net.pretronic.dkperms.minecraft.commands.rank.RankCommand;
import net.pretronic.dkperms.minecraft.config.DKPermsConfig;
import net.pretronic.dkperms.minecraft.integration.DKPermsPlaceholders;
import net.pretronic.dkperms.minecraft.migration.DKPermsLegacyMigration;
import net.pretronic.dkperms.minecraft.migration.cloudnet.CloudNetV2CPermsMigration;
import net.pretronic.dkperms.minecraft.migration.cloudnet.CloudNetV3CPermsMigration;
import net.pretronic.dkperms.minecraft.migration.luckperms.LuckPermsMigration;
import net.pretronic.dkperms.minecraft.migration.permissionsex.PermissionsExMigration;
import net.pretronic.libraries.document.Document;
import net.pretronic.libraries.document.type.DocumentFileType;
import net.pretronic.libraries.plugin.description.PluginVersion;
import net.pretronic.libraries.plugin.lifecycle.Lifecycle;
import net.pretronic.libraries.plugin.lifecycle.LifecycleState;
import net.pretronic.libraries.plugin.service.ServicePriority;
import net.pretronic.libraries.synchronisation.UnconnectedSynchronisationCaller;
import net.pretronic.libraries.utility.GeneralUtil;
import net.pretronic.libraries.utility.concurrent.AsyncExecutor;
import net.pretronic.libraries.utility.duration.DurationProcessor;
import net.pretronic.libraries.utility.io.FileUtil;
import net.pretronic.libraries.utility.io.IORuntimeException;
import org.mcnative.common.McNative;
import org.mcnative.common.event.player.login.MinecraftPlayerLoginEvent;
import org.mcnative.common.player.MinecraftPlayer;
import org.mcnative.common.plugin.MinecraftPlugin;
import org.mcnative.common.serviceprovider.permission.PermissionProvider;
import org.mcnative.common.serviceprovider.placeholder.PlaceholderProvider;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class DKPermsPlugin extends MinecraftPlugin {

    @Lifecycle(state = LifecycleState.BOOTSTRAP)
    public void onBootstrap(LifecycleState state){
        getLogger().info("DKPerms is starting, please wait..");
        copyLegacyConfig();

        getConfiguration().load(DKPermsConfig.class);

        PluginVersion version = getDescription().getVersion();

        DefaultPermissionScopeManager scopeManager = new DefaultPermissionScopeManager();
        DefaultPermissionObjectManager objectManager = new DefaultPermissionObjectManager();

        DKPerms dkPerms = new DefaultDKPerms(version.getName()
                ,version.getBuild(),getLogger()
                ,new DefaultMigrationAssistant()
                ,new PDQStorage(getDatabaseOrCreate())
                ,new DefaultAuditLog()
                ,scopeManager
                ,objectManager
                ,new AsyncExecutor(GeneralUtil.getDefaultExecutorService())
                ,new MinecraftFormatter()
                ,new DefaultPermissionAnalyser());
        dkPerms.getAnalyser().addListener(request -> getLogger().info("[Analyser] "+request.getTarget().getName()+" | "+request.getPermission()+" -> "+request.getResult()));

        DKPerms.setInstance(dkPerms);

        if(getRuntime().isNetworkAvailable()){
            getRuntime().getNetwork().getMessenger().registerSynchronizingChannel("dkperms_objects"
                    ,this,Integer.class,objectManager.getObjects());
            getRuntime().getNetwork().getMessenger().registerSynchronizingChannel("dkperms_scopes",
                    this,Integer.class,scopeManager.getScopeSynchronizer());
            DefaultPermissionObject.SYNCHRONISATION_CALLER = objectManager.getObjects().getCaller();
        }else{
            objectManager.getObjects().initUnconnected();
            scopeManager.getScopeSynchronizer().init(new UnconnectedSynchronisationCaller<>(true));
        }

        scopeManager.initialise(dkPerms);
        objectManager.initialise(dkPerms);

        PermissionObjectType.USER_ACCOUNT.setLocalHolderFactory(new UserFactory());

        DKPermsConfig.load();

        findCurrentInstanceScope(scopeManager);

        getRuntime().getRegistry().registerService(this,PermissionProvider.class,new DKPermsPermissionProvider(),ServicePriority.HIGHEST);
        getRuntime().getPlayerManager().registerPlayerAdapter(PermissionObject.class,new PlayerAdapter());

        PlaceholderProvider placeholderProvider = getRuntime().getRegistry().getServiceOrDefault(PlaceholderProvider.class);
        if(placeholderProvider != null) placeholderProvider.registerPlaceHolders(this,"dkperms",new DKPermsPlaceholders());

        if(getRuntime().getPlatform().isService()){
            getRuntime().getLocal().getEventBus().subscribe(this,new MinecraftServiceListener());
        }

        getRuntime().getLocal().getEventBus().subscribe(this, MinecraftPlayerLoginEvent.class
                ,event -> event.getPlayer().getPermissionHandler());

        registerCommands();
        registerMigrations(dkPerms);
        DescriberRegistrar.register();

        getLogger().info("DKPerms started successfully");

        if(DKPermsConfig.DELETE_TIMED_OUT_ENTRIES_ENABLED){
            Duration duration = DurationProcessor.getStandard().parse(DKPermsConfig.DELETE_TIMED_OUT_ENTRIES_INTERVAL);
            getRuntime().getScheduler().createTask(this)
                    .interval(duration.getSeconds(), TimeUnit.SECONDS)
                    .execute(() -> dkPerms.getStorage().deleteTimedOutEntries())
                    .addListener(future -> {
                        if(future.isFailed()){
                            getRuntime().getLogger().error("Failed deleting timed out entries");
                            future.getThrowable().printStackTrace();
                        }
                    });
        }
    }

    @Lifecycle(state = LifecycleState.SHUTDOWN)
    public void onShutdown(LifecycleState state){
        getLogger().info("DKPerms is shutting down, please wait..");
        DKPerms.setInstance(null);
        getLogger().info("DKPerms successfully shutted down");
    }

    private void copyLegacyConfig(){
        File configLocation = new File("plugins/DKPerms/config.yml");

        if(configLocation.exists()) {
            Document oldConfig = DocumentFileType.YAML.getReader().read(configLocation);

            if(oldConfig.contains("storage.mysql")) {
                getLogger().info("DKPerms Legacy detected");

                File legacyConfigLocation = new File("plugins/DKPerms/legacy-config.yml");
                try{
                    FileUtil.copyFile(configLocation, legacyConfigLocation);
                    boolean success = configLocation.delete();
                    if(success) {
                        getLogger().info("DKPerms Legacy config successfully copied to legacy-config.yml");
                        return;
                    }
                }catch (IORuntimeException e){
                    e.printStackTrace();
                }
                getLogger().error("DKPerms Legacy config could not be copied to legacy-config.yml");
            }
        }
    }

    private void findCurrentInstanceScope(PermissionScopeManager scopeManager){
        if(DKPermsConfig.SCOPE_CURRENT_INSTANCE_DYNAMIC){
            String serverName = McNative.getInstance().getLocal().getName();

            String group = serverName.split(DKPermsConfig.SCOPE_SERVER_GROUP_SPLIT)[0];

            PermissionScope groupScope = scopeManager.getNamespace(DKPermsConfig.SCOPE_NAMESPACE)
                    .getChild("serverGroup",group);
            PermissionScope scope = groupScope.getChild("server",serverName);
            scopeManager.setCurrentInstanceScope(scope);
            DKPermsConfig.SCOPE_CURRENT_INSTANCE_SCOPE = scope;
            DKPermsConfig.SCOPE_CURRENT_GROUP_SCOPE = groupScope;
        }else{
            String pathInstance = DKPermsConfig.SCOPE_NAMESPACE+"\\"+DKPermsConfig.SCOPE_CURRENT_INSTANCE_SCOPE_NAME;
            String pathGroup = DKPermsConfig.SCOPE_NAMESPACE+"\\"+DKPermsConfig.SCOPE_CURRENT_GROUP_SCOPE_NAME;
            scopeManager.setCurrentInstanceScope(scopeManager.get(pathInstance));
            DKPermsConfig.SCOPE_CURRENT_GROUP_SCOPE = scopeManager.get(pathGroup);
        }

        DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_GROUP = setupManagementScope(scopeManager,DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_GROUP_NAME);
        DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_PERMISSION = setupManagementScope(scopeManager,DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_PERMISSION_NAME);
        DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_OPERATOR = setupManagementScope(scopeManager,DKPermsConfig.MCNATIVE_MANAGEMENT_SCOPE_OPERATOR_NAME);
    }

    private PermissionScope setupManagementScope(PermissionScopeManager scopeManager,String path){
        if(path.equalsIgnoreCase("{global}")){
            return scopeManager.getNamespace(DKPermsConfig.SCOPE_NAMESPACE);
        }else  if(path.equalsIgnoreCase("{currentGroup}")){
            return DKPermsConfig.SCOPE_CURRENT_GROUP_SCOPE;
        }else  if(path.equalsIgnoreCase("{currentInstance}")){
            return DKPermsConfig.SCOPE_CURRENT_INSTANCE_SCOPE;
        }else{
            return scopeManager.get(path);
        }
    }

    private void registerCommands(){
        getRuntime().getLocal().getCommandManager().registerCommand(new PermissionCommand(this,DKPermsConfig.COMMAND_PERMISSION));
        getRuntime().getLocal().getCommandManager().registerCommand(new RankCommand(this,DKPermsConfig.COMMAND_RANK));
        getRuntime().getLocal().getCommandManager().registerCommand(new TeamCommand(this,DKPermsConfig.COMMAND_TEAM));
    }

    private void registerMigrations(DKPerms dkPerms){
        dkPerms.getMigrationAssistant().registerMigration(new DKPermsLegacyMigration());
        dkPerms.getMigrationAssistant().registerMigration(new CloudNetV2CPermsMigration());
        dkPerms.getMigrationAssistant().registerMigration(new CloudNetV3CPermsMigration());
        dkPerms.getMigrationAssistant().registerMigration(new PermissionsExMigration());
        dkPerms.getMigrationAssistant().registerMigration(new LuckPermsMigration());
    }

    private static class PlayerAdapter implements Function<MinecraftPlayer, PermissionObject> {

        private PlayerAdapter(){}

        @Override
        public PermissionObject apply(MinecraftPlayer player) {
            PermissionObject object = DKPerms.getInstance().getObjectManager().getObjectByAssignment(player.getUniqueId());
            if(object == null){
                object = DKPerms.getInstance().getObjectManager().createObject(
                        DKPermsConfig.OBJECT_PLAYER_SCOPE
                        ,PermissionObjectType.USER_ACCOUNT
                        ,player.getName(),player.getUniqueId());
            }else if(!object.getName().equals(player.getName())){
                object.setName(DKPerms.getInstance().getObjectManager().getSuperAdministrator(),player.getName());
            }
            return object;
        }
    }

    private static class UserFactory implements PermissionHolderFactory {

        @Override
        public Object create(PermissionObject object) {
            return McNative.getInstance().getPlayerManager().getPlayer(object.getAssignmentId());
        }
    }

}
