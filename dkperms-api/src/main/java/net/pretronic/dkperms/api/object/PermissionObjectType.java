/*
 * (C) Copyright 2019 The DKPerms Project (Davide Wietlisbach & Philipp Elvin Friedhoff)
 *
 * @author Davide Wietlisbach
 * @since 28.10.19, 19:40
 * @website %web%
 *
 * %license%
 */

package net.pretronic.dkperms.api.object;

import java.util.concurrent.CompletableFuture;

public interface PermissionObjectType {

    PermissionObjectType SERVICE_ACCOUNT = new BuiltInPermissionObjectType(1,"BUILTIN/SERVICE_ACCOUNT","Service",false);

    PermissionObjectType USER_ACCOUNT = new BuiltInPermissionObjectType(1,"BUILTIN/SERVICE_ACCOUNT","User",false);

    PermissionObjectType GROUP = new BuiltInPermissionObjectType(1,"BUILTIN/GROUP","Group",true);


    int getId();

    String getName();

    String getDisplayName();

    void rename(String name, String displayName);

    CompletableFuture<Void> renameAsync(String name, String displayName);

    boolean isParentAble();

    boolean hasLocalHolderFactory();

    PermissionHolderFactory getLocalHolderFactory();

    void setLocalHolderFactory(PermissionHolderFactory factory);


    default void checkParentAble(){
        if(!isParentAble()) throw new IllegalArgumentException("Object "+getName()+" is not a group object");
    }

}
