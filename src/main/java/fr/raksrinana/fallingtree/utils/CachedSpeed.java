package fr.raksrinana.fallingtree.utils;

import net.minecraft.util.math.BlockPos;
import java.util.Objects;

public class CachedSpeed{
	private final BlockPos pos;
	private float speed;
	private long millis;
	
	public CachedSpeed(BlockPos pos, float speed){
		this.pos = pos;
		this.speed = speed;
		this.millis = System.currentTimeMillis();
	}
	
	public boolean isValid(BlockPos blockPos){
		return millis + 1000 >= System.currentTimeMillis() && Objects.equals(pos, blockPos);
	}
	
	public float getSpeed(){
		return speed;
	}
}
