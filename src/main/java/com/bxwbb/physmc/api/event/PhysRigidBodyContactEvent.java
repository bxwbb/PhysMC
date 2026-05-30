package com.bxwbb.physmc.api.event;

import com.bxwbb.physmc.api.PhysBody;
import org.bukkit.Location;
import org.joml.Vector3d;

/**
 * 两个 PhysMC 物理刚体发生接触时触发的事件。
 */
public class PhysRigidBodyContactEvent extends PhysContactEvent {

    private final PhysBody otherBody;

    public PhysRigidBodyContactEvent(PhysBody body, PhysBody otherBody, Location contactPoint, Vector3d normal, double impulse) {
        super(body, PhysContactTargetType.RIGID_BODY, contactPoint, normal, impulse);
        this.otherBody = otherBody;
    }

    /**
     * 返回另一个发生接触的物理刚体。
     *
     * @return 另一个物理刚体
     */
    public PhysBody getOtherPhysBody() {
        return otherBody;
    }

    /**
     * 返回另一个发生接触的物理刚体。
     *
     * @return 另一个物理刚体
     * @deprecated 请使用 {@link #getOtherPhysBody()}。
     */
    @Deprecated
    public PhysBody getOtherBody() {
        return otherBody;
    }
}
