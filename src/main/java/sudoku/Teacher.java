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

	/**
	 * Message received after registering an agent.
	 * Extended to provide info whether it was a Player or a Table that was registered.
	 */
	public static class RegisteredMsg implements Protocol, SharedProtocols.RegisteringProtocol
	{
		final int _agentId;
		final boolean _isItDone;
		public RegisteredMsg(int agentId, boolean isItDone)
		{
			this._agentId = agentId;
			this._isItDone = isItDone;
		}
	}

	/** Message received after registering a Player. */
	public static class RegisteredPlayerMsg extends RegisteredMsg
	{
		final int _playerId;
		public RegisteredPlayerMsg(int playerId, boolean isItDone)
		{
			super(playerId, isItDone);
			this._playerId = playerId;
		}
	}

	/** Message received after registering a Table. */
	public static class RegisteredTableMsg extends RegisteredMsg
	{
		final int _tableId;
		public RegisteredTableMsg(int tableId, boolean isItDone)
		{
			super(tableId, isItDone);
			this._tableId = tableId;
		}
	}

	/**
	 * Reply for a request for memorised Digits and Masks by the Player.
	 * Memory is represented by a HashMap, where a key is (global) tableId, and value is a Pair of Digit and Mask.
	 */
	public static class MemorisedDigitsMsg implements Protocol, SharedProtocols.InspectionProtocol
	{
		public final Map<Integer,Pair<Integer,Boolean>> _memorisedDigits;
		public final int _requestedPlayerId;
		public MemorisedDigitsMsg(Map<Integer,Pair<Integer,Boolean>> memorisedDigits, int requestedPlayerId)
		{
			this._memorisedDigits = memorisedDigits;
			this._requestedPlayerId = requestedPlayerId;
		}
	}

	/** Message commanding the Teacher to inspect it's Players' digits. */
	public static class InspectChildDigitsMsg implements Protocol, SharedProtocols.InspectionProtocol
	{
		public final int[][] _board;
		public final ActorRef<SudokuSupervisor.Command> _replyTo;
		public InspectChildDigitsMsg(int[][] board, ActorRef<SudokuSupervisor.Command> replyTo)
		{
			this._board = board;
			this._replyTo = replyTo;
		}
	}

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
				.onMessage(InspectChildDigitsMsg.class, this::onInspectChildDigits)
				.onSignal(PreRestart.class, signal -> onPreRestart())
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Behaviour towards crashing message - simulates Teacher crashing.
	 * @param simulateCrashMsg	crashing message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onSimulateCrash(SimulateCrashMsg simulateCrashMsg)
	{
		System.out.println("Teacher is simulating crash.");
		throw new RuntimeException("I crashed!");
	}

	/**
	 * Sends MemorisedDigitsRequestMsg to all child Players.
	 *
	 * @param msg	message commanding Teacher to inspect Players
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onInspectChildDigits(InspectChildDigitsMsg msg)
	{
		ActorRef<Player.Protocol> player;

		for(int playerId = 0; playerId < _sudoku.getPlayerCount(); playerId++)
		{
			player = _players.get(playerId);
			player.tell(new Player.MemorisedDigitsRequestMsg(getContext().getSelf(), getTableIdsForPlayerId(playerId)));
		}
		return this;
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
		for (int playerId = 0; playerId < _sudoku.getPlayerCount(); ++playerId)
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

	/**
	 * For given Player, registers to it the Table assigned to given position: (x,y).
	 * Also, to the very same Table registers given Player.
	 * On registration on the particular Position, there is also passed to the Player a particular piece of Sudoku.
	 * @param playerRef		a reference to the Player to whom a Table should be registered (and vice versa)
	 * @param playerId		ID of the Player
	 * @param sudokuSize	a size of the Sudoku
	 * @param x				x coordinate of the Position of the Table
	 * @param y				y coordinate of the Position of the Table
	 */
	private void registerMutually(ActorRef<Player.Protocol> playerRef, int playerId, int sudokuSize, int x, int y)
	{
		final int tableId = sudokuSize * y + x;
		ActorRef<Table.Protocol> tableRef = _tables.get(tableId);
		playerRef.tell(new Player.RegisterTableMsg(
				tableRef,
				tableId,
				_sudoku.getDigit(x, y),
				_sudoku.getMask(x, y),
				getContext().getSelf()
				));
		tableRef.tell(new Table.RegisterPlayerMsg(
				playerRef,
				playerId,
				getContext().getSelf()
				));
	}

	/**
	 * Action of registering all Players to Tables and all Tables to Players during startup.
	 * On registration on the particular Position, there is also passed to the Player a particular piece of Sudoku.
	 */
	private void registerAgentsOnSetup()
	{
		final int sudokuSize = _sudoku.getSize();
		int playerId = 0;
		ActorRef<Player.Protocol> playerRef;

		// Register columns
		for(int x = 0; x < sudokuSize; ++x, ++playerId)
		{
			playerRef = _players.get(playerId);
			for(int y = 0; y < sudokuSize; ++y)
			{
				registerMutually(playerRef, playerId, sudokuSize, x, y);
			}
		}
		// Register rows
		for(int y = 0; y < sudokuSize; ++y, ++playerId)
		{
			playerRef = _players.get(playerId);
			for(int x = 0; x < sudokuSize; ++x)
			{
				registerMutually(playerRef, playerId, sudokuSize, x, y);
			}
		}
		// Register blocks (squares)
		final int sudokuRank = _sudoku.getRank();
		for(int y = 0; y < sudokuSize; y += sudokuRank)
		{
			for(int x = 0; x < sudokuSize; x += sudokuRank, ++playerId)
			{
				playerRef = _players.get(playerId);
				for(int j = 0; j < sudokuRank; ++j)
				{
					for(int i = 0; i < sudokuRank; ++i)
					{
						registerMutually(playerRef, playerId, sudokuSize, x + i, y + j);
					}
				}
			}
		}
	}

	/**
	 * Generates array of tableIds for given playerId.
	 * @param playerId	(global) ID of a Player
	 * @return			array of IDs of Tables matching to given Player
	 */
	private int[] getTableIdsForPlayerId(int playerId)
	{
		int i, tableId, sudokuSize = _sudoku.getSize(), sudokuRank = _sudoku.getRank();
		int[] tableIDs = new int[sudokuSize];

		// Player is a Column
		if(playerId >= 0 && playerId < sudokuSize)
		{
			for (i = 0, tableId = playerId; i < sudokuSize; i++, tableId += sudokuSize)
				tableIDs[i] = tableId;
		}
		// Row
		else if(playerId >= sudokuSize && playerId < 2 * sudokuSize)
		{
			for (i = 0, tableId = (playerId - sudokuSize) * sudokuSize; i < sudokuSize; i++, tableId += 1)
				tableIDs[i] = tableId;
		}
		// Block
		else if(playerId >= 2 * sudokuSize && playerId < 3 * sudokuSize)
		{
			int r = (playerId - 2* sudokuSize) % sudokuRank;
			int initTableId = (playerId - 2* sudokuSize - r) * sudokuSize + r * sudokuRank;

			for (i = 0, tableId = initTableId; i < sudokuRank; i++, tableId += sudokuSize)
				for (int j = 0; j < sudokuRank; j++)
					tableIDs[sudokuRank * i + j] = tableId + j;
		}
		else
		{
			throw new RuntimeException("Given playerId is out of range.");
		}
		return tableIDs;
	}
}
