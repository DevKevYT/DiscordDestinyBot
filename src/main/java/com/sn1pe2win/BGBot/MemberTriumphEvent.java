package com.sn1pe2win.BGBot;

import com.sn1pe2win.BGBot.RoleManager.BotRole;

import discord4j.core.object.entity.Member;

public class MemberTriumphEvent {

	BotRole current;
	BotRole previous;
	int progressValue;
	Member member;
	
	MemberTriumphEvent() {
	}

	/**May be null
	 * @Nullable*/
	public BotRole getCurrentTriumph() {
		return current;
	}
	
	/**May be null
	 * @Nullable*/
	public BotRole getPreviousTriumph() {
		return previous;
	}
	
	public int getProgressValue() {
		return progressValue;
	}
	
	public Member getMember() {
		return member;
	}
}
