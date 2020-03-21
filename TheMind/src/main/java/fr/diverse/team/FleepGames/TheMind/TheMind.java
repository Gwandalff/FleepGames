package fr.diverse.team.FleepGames.TheMind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import fr.diverse.team.FleepGameEngine.creator.AbstractGameSpecification;
import fr.diverse.team.FleepGameEngine.creator.Game;
import fr.diverse.team.FleepGameEngine.creator.GameBuilder;
import fr.diverse.team.FleepGameEngine.creator.GameEvent;
import fr.diverse.team.FleepGameEngine.creator.GameRequests;

public class TheMind extends AbstractGameSpecification {
	
	private static String convID;
	private static Map<String, String> playerConv;
	
	private static List<String> users;
	private static Map<String, List<Integer>> cards;
	private static Integer stack;
	private static int level;
	private static int life;
	private static int shuriken;
	private static Map<String, Boolean> shurikenVote;
	
	private static final int MAX_ROUND = 12;

	@Override
	public Game getGame() {
		return new GameBuilder()
				.startingToken("Mind")
				.constructor(TheMind::init)
				.addGameEvent("play", TheMind::play)
				.addGameEvent("vote", TheMind::askShuriken)
				.addGameEvent("for", TheMind::forShuriken)
				.addGameEvent("against", TheMind::againstShuriken)
				.winChecker(TheMind::check)
				.destructor(TheMind::destructor)
				.build();
		
	}

	@Override
	public String getName() {
		return "TheMind";
	}
	
	public static void init(List<String> userIds) {
		users = userIds;
		level = 1;
		life = 4;
		shuriken = 1;
		cards = new HashMap<String, List<Integer>>();
		convID = GameRequests.createConv(users, "The Mind");
		playerConv = new HashMap<String, String>();
		for (String id : userIds) {
			playerConv.put(id, GameRequests.createPrivateConv(id, "The Mind - Your Cards"));
		}
		startRound();
	}
	
	private static void startRound() {
		GameRequests.sendMessage(convID, "Begenning of the round "+level);
		stack = 0;
		int numberOfCards = users.size() * level;
		List<Integer> cardsForRound = new ArrayList<Integer>();
		while(cardsForRound.size()<numberOfCards) {
			Integer value = new Random().nextInt(100) + 1;
			if(!cardsForRound.contains(value)) {
				cardsForRound.add(value);
			}
		}
		for (String user : users) {
			List<Integer> cardsForPlayer = new ArrayList<Integer>();
			for (int i = 0; i < level; i++) {
				int index = new Random().nextInt(cardsForRound.size());
				cardsForPlayer.add(cardsForRound.remove(index));
			}
			Collections.sort(cardsForPlayer);
			cards.put(user, cardsForPlayer);
		}
		Set<String> playerWithCards = cards.keySet();
		for (String id : playerWithCards) {
			String conv = playerConv.get(id);
			if(conv != null) {
				String msg = "Your cards for this turn are :";
				List<Integer> pCards = cards.get(id);
				for (Integer card : pCards) {
					msg += " " + card;
				}
				GameRequests.sendMessage(conv, msg);
			}
		}
	}

	public static void play(GameEvent e) {
		if (shurikenVote != null) {
			GameRequests.sendMessage(convID, "Please finish the vote before playing");
			return;
		}
		List<Integer> playerCards = cards.get(e.getUserId());
		Integer move = playerCards.remove(0);
		stack = move;
		String conv = playerConv.get(e.getUserId());
		if(conv != null) {
			String msg = "Your remaining cards are :";
			for (Integer card : playerCards) {
				msg += " " + card;
			}
			GameRequests.sendMessage(conv, msg);
		}
		GameRequests.sendMessage(convID, "The current card on top of the stack is : "+move);
	}
	
	public static void askShuriken(GameEvent e) {
		shurikenVote = new HashMap<String, Boolean>();
		GameRequests.sendMessage(convID, "Somebody started a vote for shuriken please vote");
	}
	
	public static void forShuriken(GameEvent e) {
		if (shurikenVote == null) {
			GameRequests.sendMessage(convID, "Please start a shuriken vote before voting");
			return;
		}
		shurikenVote.put(e.getUserId(), true);
	}
	
	public static void againstShuriken(GameEvent e) {
		if (shurikenVote == null) {
			GameRequests.sendMessage(convID, "Please start a shuriken vote before voting");
			return;
		}
		shurikenVote.put(e.getUserId(), false);
	}
	
	private static void applyShuriken() {
		Set<String> players = cards.keySet();
		String out = "The smallest card of each are :";
		for (String player : players) {
			List<Integer> pCards = cards.get(player);
			if(!pCards.isEmpty()) {
				out += " "+pCards.get(0);
			}
		}
		shuriken--;
		GameRequests.sendMessage(convID, out);
		shurikenVote = null;
	}
	
	public static boolean check() {
		if(shurikenVote != null && shurikenVote.size() == users.size()) {
			Collection<Boolean> votes = shurikenVote.values();
			votes.removeIf((Boolean b)->b.booleanValue());
			int against = votes.size();
			GameRequests.sendMessage(convID, "Results :\n"+(users.size()-against)+" for\n"+against+" against");
			if(against == 0) {
				applyShuriken();
			}
			return false;
		}
		boolean cardsRemain = false;
		for (String user : users) {
			List<Integer> playerCards = cards.get(user);
			for (int i = 0; i < playerCards.size(); i++) {
				cardsRemain = true;
				if(playerCards.get(i) < stack) {
					int card = playerCards.remove(i);
					life--;
					i--;
					GameRequests.sendMessage(convID, "The card "+card+" was bypassed, you lose one life");
				}
			}
		}
		if(life <= 0) {
			GameRequests.sendMessage(convID, "You've lost !");
			return true;
		}
		if(!cardsRemain) {
			GameRequests.sendMessage(convID, "You've win this round !");
			level++;
			if (level > MAX_ROUND) {
				GameRequests.sendMessage(convID, "Congratulations, you have finished \"The Mind\" !");
				return true;
			}
			startRound();
			GameRequests.sendMessage(convID, "Remaining shuriken : "+shuriken);
		}
		GameRequests.sendMessage(convID, "Remaining lives : "+life);
		return false;
	}
	
	public static boolean destructor() {
		GameRequests.sendMessage(convID, "This conversation will self-destruct in five seconds");
		try {
			Thread.sleep(5000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		GameRequests.deleteConv(convID);
		Set<String> players = playerConv.keySet();
		for (String player : players) {
			GameRequests.deleteConv(playerConv.get(player));
		}
		return true;
	}
}
