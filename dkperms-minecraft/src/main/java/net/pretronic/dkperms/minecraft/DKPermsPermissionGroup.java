/*
 * (C) Copyright 2019 The DKPerms Project (Davide Wietlisbach & Philipp Elvin Friedhoff)
 *
 * @author Davide Wietlisbach
 * @since 07.12.19, 18:23
 * @website %web%
 *
 * %license%
 */

package net.pretronic.dkperms.minecraft;

import net.pretronic.dkperms.api.object.PermissionObject;
import net.pretronic.dkperms.api.object.holder.PermissionObjectHolder;

public class DKPermsPermissionGroup implements PermissionObjectHolder {

    private final PermissionObject object;

    public DKPermsPermissionGroup(PermissionObject object) {
        this.object = object;
    }

    @Override
    public PermissionObject getObject() {
        return object;
    }

}
