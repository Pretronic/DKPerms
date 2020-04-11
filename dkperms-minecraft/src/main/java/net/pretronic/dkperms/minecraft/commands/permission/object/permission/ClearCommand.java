/*
 * (C) Copyright 2020 The DKPerms Project (Davide Wietlisbach & Philipp Elvin Friedhoff)
 *
 * @author Davide Wietlisbach
 * @since 01.02.20, 21:38
 * @website %web%
 *
 * %license%
 */

package net.pretronic.dkperms.minecraft.commands.permission.object.permission;

import net.pretronic.libraries.command.command.configuration.CommandConfiguration;
import net.pretronic.libraries.command.command.object.ObjectCommand;
import net.pretronic.libraries.command.sender.CommandSender;
import net.pretronic.libraries.message.bml.variable.VariableSet;
import net.pretronic.libraries.utility.interfaces.ObjectOwner;
import net.pretronic.dkperms.api.object.PermissionObject;
import net.pretronic.dkperms.api.scope.PermissionScope;
import net.pretronic.dkperms.minecraft.commands.CommandUtil;
import net.pretronic.dkperms.minecraft.config.Messages;

public class ClearCommand extends ObjectCommand<PermissionObject> {

    public ClearCommand(ObjectOwner owner) {
        super(owner, CommandConfiguration.name("clear","c"));
    }

    @Override
    public void execute(CommandSender sender, PermissionObject object, String[] arguments) {
        PermissionScope scope = CommandUtil.readScope(sender,object,arguments,0);
        if(scope == null) return;

        object.clearPermission(null,scope);

        VariableSet variables = VariableSet.create();
        variables.add("object",object.getName());
        variables.add("scope",scope.getPath());
        sender.sendMessage(Messages.OBJECT_PERMISSION_CLEAR,variables);
    }
}
