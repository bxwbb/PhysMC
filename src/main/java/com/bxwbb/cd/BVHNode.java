package com.bxwbb.cd;

import com.bxwbb.phy.RigidBody;
import java.util.List;

public class BVHNode<BoundingVolumeClass extends BoundingVolume> {

    @SuppressWarnings("unchecked")
    public BVHNode<BoundingVolumeClass>[] children = (BVHNode<BoundingVolumeClass>[]) new BVHNode[2];
    public BoundingVolumeClass volume;
    public RigidBody body;
    public BVHNode<BoundingVolumeClass> parent;
    private final Class<? extends BoundingVolumeClass> clazz;

    public BVHNode(BVHNode<BoundingVolumeClass> parent, BoundingVolumeClass volume) {
        this(parent, volume, null);
    }

    @SuppressWarnings("unchecked")
    public BVHNode(BVHNode<BoundingVolumeClass> parent, BoundingVolumeClass volume, RigidBody body) {
        this.parent = parent;
        this.volume = volume;
        this.body = body;
        this.clazz = (Class<? extends BoundingVolumeClass>) volume.getClass();
    }

    public boolean isLeaf() {
        return body != null;
    }

    public int getPotentialContacts(List<PotentialContact> contacts, int index, int limit) {
        if (isLeaf() || limit == 0) return 0;
        return children[0].getPotentialContactsWith(children[1], contacts, index, limit);
    }

    public boolean overlaps(BVHNode<BoundingVolumeClass> other) {
        return volume.overlaps(other.volume);
    }

    public int getPotentialContactsWith(BVHNode<BoundingVolumeClass> other, List<PotentialContact> contacts, int index, int limit) {
        if (!overlaps(other) || limit == 0) return 0;
        if (isLeaf() && other.isLeaf()) {
            contacts.get(index).body[0] = body;
            contacts.get(index).body[1] = other.body;
            return 1;
        }
        if (other.isLeaf() || (!isLeaf() && volume.getSize() >= other.volume.getSize())) {
            int count = children[0].getPotentialContactsWith(other, contacts, index, limit);
            if (limit > count) {
                return count + children[1].getPotentialContactsWith(other, contacts, index + count, limit - count);
            } else {
                return count;
            }
        } else {
            int count = getPotentialContactsWith(other.children[0], contacts, index, limit);
            if (limit > count) {
                return count + getPotentialContactsWith(other.children[1], contacts, index + count, limit - count);
            } else {
                return count;
            }
        }
    }

    public void insert(RigidBody newBody, BoundingVolumeClass newVolume) {
        if (isLeaf()) {
            children[0] = new BVHNode<>(this, volume, body);
            children[1] = new BVHNode<>(this, newVolume, newBody);
            this.body = null;
            recalculateBoundingVolume();
        } else {
            if (children[0].volume.getGrowth(newVolume) < children[1].volume.getGrowth(newVolume)) {
                children[0].insert(newBody, newVolume);
            } else {
                children[1].insert(newBody, newVolume);
            }
        }
    }

    public void recalculateBoundingVolume() {
        recalculateBoundingVolume(true);
    }

    public void recalculateBoundingVolume(boolean recurse) {
        if (isLeaf()) return;
        try {
            volume = clazz.getConstructor(clazz, clazz).newInstance(children[0].volume, children[1].volume);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (parent != null) parent.recalculateBoundingVolume(true);
    }

    public void delete() {
        if (parent != null) {
            BVHNode<BoundingVolumeClass> sibling;
            if (parent.children[0] == this) sibling = parent.children[1];
            else sibling = parent.children[0];
            parent.volume = sibling.volume;
            parent.body = sibling.body;
            parent.children[0] = sibling.children[0];
            parent.children[1] = sibling.children[1];
            sibling.parent = null;
            sibling.body = null;
            sibling.children[0] = null;
            sibling.children[1] = null;
            sibling.delete();
            parent.recalculateBoundingVolume();
        }
        if (children[0] != null) {
            children[0].parent = null;
            children[0].delete();
        }
        if (children[1] != null) {
            children[1].parent = null;
            children[1].delete();
        }
    }

}