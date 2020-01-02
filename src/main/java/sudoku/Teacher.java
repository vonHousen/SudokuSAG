package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent that interprets development of the playing agents and rewards them. Singleton.
 * Parent of both Players and Tables.
 */
public class Teacher extends AbstractBehavior<Teacher.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message for creating the Teacher. */
	public static class CreateMsg implements Protocol
	{
		final String _name;
		final Sudoku _sudoku;

		public CreateMsg(String name, Sudoku sudoku)
		{
			this._name = name;
			this._sudoku = sudoku;
		}
	}

	/** Sudoku riddle to be solved. */
	private final Sudoku _sudoku;
	/** Data structure for storing all Players - child agents. */
	private final Map<Integer, ActorRef<Player.Protocol>> _players = new HashMap<>();

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return N/A
	 */
	public static Behavior<Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Teacher(context, createMsg));
	}

	private Teacher(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		this._sudoku = createMsg._sudoku;
		context.getLog().info("Teacher created");

		spawnPlayers();
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return N/A
	 */
	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.build();
	}

	private void spawnPlayers()
	{
		for(int playerId = 0; playerId < 3 * _sudoku.getSize(); playerId++)
		{
			// TODO in fact spawn Row / Column / Block
			ActorRef<Player.Protocol> newPlayer =
					getContext().spawn(Player.create(new Player.CreateMsg(playerId)), "player-" + playerId);
			_players.put(playerId, newPlayer);

			// getContext().watchWith(newPlayer, new DeviceGroupTerminated(groupId)); TODO
		}
	}
}
