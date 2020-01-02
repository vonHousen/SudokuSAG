package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
import akka.actor.typed.SupervisorStrategy;
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
		final ActorRef<SudokuSupervisor.Command> _replyTo;

		public CreateMsg(String name, Sudoku sudoku, ActorRef<SudokuSupervisor.Command> replyTo)
		{
			this._name = name;
			this._sudoku = sudoku;
			this._replyTo = replyTo;
		}
	}

	/** Message for making the Teacher crash. */
	public static class SimulateCrashMsg implements Protocol {}

	/** Sudoku riddle to be solved. */
	private final Sudoku _sudoku;
	/** Parent agent */
	private final ActorRef<SudokuSupervisor.Command> _parent;
	/** Data structure for storing all Players - child agents. */
	private final Map<Integer, ActorRef<Player.Protocol>> _players;
	/** Data structure for storing all Tables - child agents. */
	private final Map<Integer, ActorRef<Table.Protocol>> _tables;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return 		wrapped Behavior
	 */
	public static Behavior<Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Teacher(context, createMsg));
	}

	private Teacher(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		this._sudoku = createMsg._sudoku;
		this._parent = createMsg._replyTo;
		this._players = new HashMap<>();
		this._tables = new HashMap<>();
		context.getLog().info("Teacher created");			// left for debugging only

		spawnPlayers();
		spawnTables();
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(SimulateCrashMsg.class, this::onSimulateCrash)
				.onSignal(PreRestart.class, signal -> onPreRestart())
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Behaviour towards crashing message - simulates Teacher crashing.
	 * @param simulateCrashMsg	crashing message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Teacher.Protocol> onSimulateCrash(SimulateCrashMsg simulateCrashMsg)
	{
		System.out.println("Teacher is simulating crash.");
		throw new RuntimeException("I crashed!");
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping agent.
	 * @return 		wrapped Behavior
	 */
	private Teacher onPostStop()
	{
		getContext().getLog().info("Teacher stopped");
		return this;
	}

	/**
	 * Handler of PreRestart signal.
	 * Expected just before restarting the agent.
	 * @return 		wrapped Behavior
	 */
	private Teacher onPreRestart()
	{
		getContext().getLog().info("Teacher will be restarted.");
		_parent.tell(new SudokuSupervisor.TeacherWillRestartMsg("I will be restarted."));
		return this;
	}

	/** Action of spawning all child Players agents. */
	private void spawnPlayers()
	{
		for(int playerId = 0; playerId < 3 * _sudoku.getSize(); playerId++)
		{
			// TODO in fact spawn Row / Column / Block
			ActorRef<Player.Protocol> newPlayer = getContext().spawn(
					//Behaviors.supervise(		TODO decide whether delete or uncomment
						Player.create(new Player.CreateMsg(playerId))
					//).onFailure(SupervisorStrategy.restart())
					, "player-" + playerId
			);
			_players.put(playerId, newPlayer);
		}
	}

	/** Action of spawning all child Tables agents. */
	private void  spawnTables()
	{
		for(int tableId = 0; tableId < 3*3 * _sudoku.getSize(); tableId++)
		{
			ActorRef<Table.Protocol> newTable = getContext().spawn(
					//Behaviors.supervise(		TODO decide whether delete or uncomment
						Table.create(new Table.CreateMsg(tableId))
					//).onFailure(SupervisorStrategy.restart())
					, "table-" + tableId
			);
			_tables.put(tableId, newTable);
		}
	}

	private void registerPlayerToTable(ActorRef<Void> player, ActorRef<Void> table)
	{
		//player.tell();
	}
}
