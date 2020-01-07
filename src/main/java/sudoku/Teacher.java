package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.PreRestart;
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
	private void spawnPlayers()		// TODO decide if pass a part of sudoku during creation
	{
		int playerId = 0, x, y;

		// spawn Rows
		for(x = 0; x < _sudoku.getRank(); playerId++, x++)
		{
			ActorRef<Player.Protocol> newPlayer = getContext().spawn(
					//Behaviors.supervise(		TODO decide whether delete or uncomment
						Player.create(new Player.CreateMsg(
							playerId,
							new Vector2d(x, 0),
							Player.Type.ROW,
							new int[_sudoku.getRank()],
							new boolean[_sudoku.getRank()]
							)
						)
					//).onFailure(SupervisorStrategy.restart())
					, "player-" + playerId
			);
			_players.put(playerId, newPlayer);
		}

		// spawn Columns
		for(y = 0; y < _sudoku.getRank(); playerId++, y++)
		{
			ActorRef<Player.Protocol> newPlayer = getContext().spawn(
					//Behaviors.supervise(		TODO decide whether delete or uncomment
						Player.create(new Player.CreateMsg(
							playerId,
							new Vector2d(0, y),
							Player.Type.COLUMN,
							new int[_sudoku.getRank()],
							new boolean[_sudoku.getRank()]
							)
						)
					//).onFailure(SupervisorStrategy.restart())
					, "player-" + playerId
			);
			_players.put(playerId, newPlayer);
		}

		// spawn Columns
		final int blockSize = _sudoku.getBlockSize();
		for(y = 0; y < _sudoku.getRank(); y += blockSize)
		{
			for(x = 0; x < _sudoku.getRank(); x += blockSize, playerId++)
			{
				ActorRef<Player.Protocol> newPlayer = getContext().spawn(
						//Behaviors.supervise(		TODO decide whether delete or uncomment
							Player.create(new Player.CreateMsg(
								playerId,
								new Vector2d(x, y),
								Player.Type.BLOCK,
								new int[_sudoku.getRank()],
								new boolean[_sudoku.getRank()]
								)
							)
						//).onFailure(SupervisorStrategy.restart())
						, "player-" + playerId
				);
				_players.put(playerId, newPlayer);
			}
		}
	}

	/** Action of spawning all child Tables agents. */
	private void  spawnTables()		// TODO decide if pass a part of sudoku during creation
	{
		int tableId = 0, x, y;
		for(y = 0; y < _sudoku.getRank(); y++)
		{
			for(x = 0; x < _sudoku.getRank(); x++, tableId++)
			{
				ActorRef<Table.Protocol> newTable = getContext().spawn(
						//Behaviors.supervise(		TODO decide whether delete or uncomment
						Table.create(new Table.CreateMsg(tableId, new Vector2d(x, y)))
						//).onFailure(SupervisorStrategy.restart())
						, "table-" + tableId
				);
				_tables.put(tableId, newTable);
			}
		}
	}

	private void registerPlayerToTable(ActorRef<Void> player, ActorRef<Void> table)
	{
		//player.tell();
	}
}
