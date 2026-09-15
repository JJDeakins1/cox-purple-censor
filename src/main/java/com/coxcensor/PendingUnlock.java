package com.coxcensor;

import java.time.Instant;
import lombok.Value;

@Value
class PendingUnlock
{
	String player;
	String item;
	Instant queuedAt;
}
