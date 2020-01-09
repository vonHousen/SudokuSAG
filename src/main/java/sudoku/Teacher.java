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
		registerAgentsOnSetup();
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
		final int sudokuSize = _sudoku.getSize();
		final int maxPlayerCount = _sudoku.getPlayerCount();
		for (int playerId = 0; playerId < maxPlayerCount; ++playerId)
		{
			ActorRef<Player.Protocol> newPlayer = getContext().spawn(
					//Behaviors.supervise(		TODO decide if supervise children
					Player.create(new Player.CreateMsg(playerId, sudokuSize)
					)
					//).onFailure(SupervisorStrategy.restart())
					, "player-" + playerId
			);
			_players.put(playerId, newPlayer);
		}
	}

	/** Action of spawning all child Tables agents. */
	private void  spawnTables()
	{
		int tableId = 0, x, y;
		final int sudokuSize = _sudoku.getSize();
		for(y = 0; y < sudokuSize; ++y)
		{
			for(x = 0; x < sudokuSize; ++x, ++tableId)
			{
				ActorRef<Table.Protocol> newTable = getContext().spawn(
						//Behaviors.supervise(		TODO decide if supervise children
						Table.create(new Table.CreateMsg(tableId, new Position(x, y)))
						//).onFailure(SupervisorStrategy.restart())
						, "table-" + tableId
				);
				_tables.put(tableId, newTable);
			}
		}
	}

	private void registerMutually(ActorRef<Player.Protocol> playerRef, int playerId, int sudokuSize, int x, int y)
	{
		final int tableId = sudokuSize*y+x;
		ActorRef<Table.Protocol> tableRef = _tables.get(tableId);
		playerRef.tell(new Player.RegisterTableMsg(
				tableRef,
				tableId,
				_sudoku.getDigit(x, y),
				_sudoku.getMask(x, y)));
		tableRef.tell(new Table.RegisterPlayerMsg(
				playerRef,
				playerId
				));
	}

	/** Action of registering Players to Tables and Tables to Players. */
	private void registerAgentsOnSetup()
	{
		final int sudokuSize = _sudoku.getSize();
		int playerId = 0;
		ActorRef<Player.Protocol> playerRef;
		// Spawn columns
		for(int x = 0; x < sudokuSize; ++x, ++playerId)
		{
			playerRef = _players.get(playerId);
			for(int y = 0; y < sudokuSize; ++y)
			{
				registerMutually(playerRef, playerId, sudokuSize, x, y);
			}
		}
		// Spawn rows
		for(int y = 0; y < sudokuSize; ++y, ++playerId)
		{
			playerRef = _players.get(playerId);
			for(int x = 0; x < sudokuSize; ++x)
			{
				registerMutually(playerRef, playerId, sudokuSize, x, y);
			}
		}
		// Spawn blocks (squares)
		final int sudokuRank = _sudoku.getRank();
		for(int y = 0; y < sudokuSize; y+=sudokuRank, ++playerId)
		{
			for(int x = 0; x < sudokuSize; x+=sudokuRank)
			{
				playerRef = _players.get(playerId);
				for(int j = 0; j < sudokuRank; ++j)
				{
					for(int i = 0; i < sudokuRank; ++i)
					{
						registerMutually(playerRef, playerId, sudokuSize, x+i, y+j);
					}
				}
			}
		}
	}
}
