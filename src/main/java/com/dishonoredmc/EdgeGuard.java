package com.dishonoredmc;

/**
 * Vanilla stops a sneaking player from walking off ledges. With toggle-crouch that blocks drop assassinations,
 * so it can be switched off (key in DishonoredClient). Player movement is simulated on the client, which is
 * where this flag is read.
 */
public final class EdgeGuard {
	public static boolean enabled = false;

	private EdgeGuard() {}
}
