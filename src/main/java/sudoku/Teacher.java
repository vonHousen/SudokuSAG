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
		final ActorRef<SudokuSupervisor.Protocol> _replyTo;

		public CreateMsg(String name, Sudoku sudoku, ActorRef<SudokuSupervisor.Protocol> replyTo)
		{
			this._name = name;
			this._sudoku = sudoku;
			this._replyTo = replyTo;
		}
	}

	/** Message for making the Teacher crash. */
	public static class SimulateCrashMsg implements Protocol
	{}

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
		public final ActorRef<Sudoku> _replyTo;
		public InspectChildDigitsMsg(ActorRef<Sudoku> replyTo)
		{
			this._replyTo = replyTo;
		}
	}

	/** The agent reports to the Teacher that it had performed reset of it's memory. */
	public static class PerformedMemoryResetMsg implements Protocol, SharedProtocols.NewIterationProtocol
	{
		public final int _id;
		public PerformedMemoryResetMsg(int id)
		{
			this._id = id;
		}
	}

	/** The Table's version of PerformedMemoryResetMsg. */
	public static class TablePerformedMemoryResetMsg extends PerformedMemoryResetMsg
	{
		public TablePerformedMemoryResetMsg(int id)
		{
			super(id);
		}
	}

	/** The Player's version of PerformedMemoryResetMsg. */
	public static class PlayerPerformedMemoryResetMsg extends PerformedMemoryResetMsg
	{
		public PlayerPerformedMemoryResetMsg(int id)
		{
			super(id);
		}
	}

	/** Message reporting negotiation's finish for a one of Tables, providing solution - a digit */
	public static class TableFinishedNegotiationsMsg implements Protocol
	{
		public final int _digit;
		public final Position _position;
		public final int _tableId;
		public TableFinishedNegotiationsMsg(int digit, Position position, int tableId)
		{
			this._digit = digit;
			this._position = position;
			this._tableId = tableId;
		}
	}

	/** Sudoku riddle to be solved. */
	private final Sudoku _sudoku;
	/** Parent agent */
	private final ActorRef<SudokuSupervisor.Protocol> _parent;
	/** Data structure for storing all Players - child agents. */
	private Map<Integer, ActorRef<Player.Protocol>> _players;
	/** Data structure for storing all Tables - child agents. */
	private Map<Integer, ActorRef<Table.Protocol>> _tables;
	/** Data structure for counting acknowledgement messages from Players and Tables. */
	private TeacherMemory _memory;
	/** Sudoku solution from the previous iteration */
	private final Sudoku _prevSudoku;
	/** HashMap for inspected digits. Key: tableId, Value: inspectedDigit. */
	private Map<Integer, Integer> _inspectedDigits;
	/** Inspector's reference. */
	private ActorRef<Sudoku> _inspector;

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
		this._sudoku = new Sudoku(createMsg._sudoku);
		this._parent = createMsg._replyTo;
		this._players = new HashMap<>();
		this._tables = new HashMap<>();
		this._memory = new TeacherMemory(_sudoku.getPlayerCount(), _sudoku.getTableCount(), _sudoku.getEmptyFieldsCount());
		this._prevSudoku = new Sudoku(this._sudoku);
		this._inspectedDigits = new HashMap<>();
		context.getLog().info("Teacher created");			// left for debugging only

		spawnPlayers();
		spawnTables();
		registerAgentsOnSetup();
		prepareForNewBigIterationAndRun();
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
				.onMessage(MemorisedDigitsMsg.class, this::onMemorisedDigits)
				.onMessage(TablePerformedMemoryResetMsg.class, this::onTablePerformedMemoryReset)
				.onMessage(PlayerPerformedMemoryResetMsg.class, this::onPlayerPerformedMemoryReset)
				.onMessage(TableFinishedNegotiationsMsg.class, this::onTableFinishedNegotiations)
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
		_inspectedDigits.clear();
		_inspector = msg._replyTo;

		for(int playerId = 0; playerId < _sudoku.getPlayerCount(); playerId++)
		{
			player = _players.get(playerId);
			player.tell(new Player.MemorisedDigitsRequestMsg(getContext().getSelf(), getTableIdsForPlayerId(playerId)));
		}
		return this;
	}

	/**
	 * Gathers received MemorisedDigitsMsg from requested Players.
	 *
	 * @param msg	inspection results
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onMemorisedDigits(MemorisedDigitsMsg msg)
	{
		int memorisedDigit;
		for(int tableId : msg._memorisedDigits.keySet())
		{
			memorisedDigit = msg._memorisedDigits.get(tableId).first;
			if(_inspectedDigits.containsKey(tableId) && _inspectedDigits.get(tableId) != memorisedDigit)
				throw new RuntimeException("Inspected digits differs on the same Position.");

			_inspectedDigits.put(tableId, memorisedDigit);
		}

		if(_inspectedDigits.keySet().size() == _tables.keySet().size())		// if it is the last msg to be received
			_inspector.tell(prepareInspectionResults());

		return this;
	}

	/**
	 * Teacher collects messages reporting it's Tables' memory being reset.
	 * @param msg	reporting message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onTablePerformedMemoryReset(TablePerformedMemoryResetMsg msg)
	{
		if (_memory.addTableReset())
		{
			startNewIteration();
		}
		return this;
	}

	/**
	 * Teacher collects messages reporting it's Players' memory being reset.
	 * @param msg	reporting message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onPlayerPerformedMemoryReset(PlayerPerformedMemoryResetMsg msg)
	{
		if (_memory.addPlayerReset())
		{
			startNewIteration();
		}
		return this;
	}

	/**
	 * Teacher collects messages reporting negotiations' solutions.
	 * When collected last message - it should call prepareForNewIterationAndRun and returnNewSolution.
	 * Note that a solution may be zero - meaning no consensus could be made with the Players.
	 * @param msg	message containing solution - a digit for a specific Position in Sudoku riddle
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onTableFinishedNegotiations(TableFinishedNegotiationsMsg msg)
	{
		_sudoku.insertDigit(msg._position.x, msg._position.y, msg._digit);
		if (_memory.addTableFinished())
		{
			returnNewSolution();
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
					//Behaviors.supervise(		TODO Kamil - decide if supervise children
					Player.create(new Player.CreateMsg(playerId, sudokuSize, getContext().getSelf())
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
						//Behaviors.supervise(		TODO Kamil - decide if supervise children
						Table.create(new Table.CreateMsg(
								tableId, new Position(x, y), sudokuSize, getContext().getSelf()))
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
		int i, tableId;
		final int sudokuSize = _sudoku.getSize(), sudokuRank = _sudoku.getRank();
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

	/**
	 * Prepares inspection results.
	 * @return	inspection results - Sudoku made of inspected Digits.
	 */
	private Sudoku prepareInspectionResults()
	{
		Sudoku results = new Sudoku(_sudoku.getRank());
		int[][] inspectionResults = new int[_sudoku.getSize()][_sudoku.getSize()];
		int digit;

		for(int tableId = 0, x = 0, y = -1; tableId < _sudoku.getSize() * _sudoku.getSize(); ++tableId, ++x)
		{
			if(tableId % _sudoku.getSize() == 0)
			{
				x = 0;
				++y;
			}
			digit = _inspectedDigits.getOrDefault(tableId, -1);
			inspectionResults[x][y] = digit;
		}
		results.setBoard(inspectionResults);
		return results;
	}

	/**
	 *  Teacher commands its agents (by sending ResetMemoryMsg) in appropriate order to reset theirs memory.
	 *  It should collect all messages in onTablePerformedMemoryReset and onPlayerPerformedMemoryReset,
	 *  and call startNewIteration method on collecting the last one.
	 */
	private void prepareForNewSmallIterationAndRun()
	{
		for(ActorRef<Table.Protocol> table : _tables.values())
			table.tell(new Table.ResetMemoryMsg(getContext().getSelf()));

		for(ActorRef<Player.Protocol> player : _players.values())
			player.tell(new Player.ResetMemorySoftlyMsg(getContext().getSelf()));
	}

	private void prepareForNewBigIterationAndRun()
	{
		for(ActorRef<Table.Protocol> table : _tables.values())
			table.tell(new Table.ResetMemoryMsg(getContext().getSelf()));

		for(ActorRef<Player.Protocol> player : _players.values())
			player.tell(new Player.ResetMemoryMsg(getContext().getSelf()));
	}

	/**
	 *  The Teacher starts new iteration of solving the sudoku.
	 *  Method is called when all Teacher's agents are ready for new iteration.
	 *  Initialisation is done by sending ConsentToStartIterationMsg to all Players.
	 */
	private void startNewIteration()
	{
		for(ActorRef<Player.Protocol> player : _players.values())
			player.tell(new Player.ConsentToStartIterationMsg());
	}

	/**
	 * After collecting all parts of current iteration's solution - all digits, Teacher returns new solution as a whole
	 * represented by Sudoku object to the parent - SudokuSupervisor.
	 */
	private void returnNewSolution()
	{
		//Sudoku newSolution = new Sudoku(_sudoku);
		//_parent.tell(new SudokuSupervisor.IterationFinishedMsg(newSolution));
		if (_sudoku.getEmptyFieldsCount() != 0)
		{
			if (!_sudoku.equals(_prevSudoku))
			{
				_prevSudoku.setBoard(_sudoku.getBoard());
				_memory.reset();
				prepareForNewSmallIterationAndRun();
			}
			else
			{
				// TODO New "big" iteration
				//_sudoku.reset();
				//prepareForNewBigIterationAndRun();

				Sudoku newSolution = new Sudoku(_sudoku);
				_parent.tell(new SudokuSupervisor.IterationFinishedMsg(newSolution));
			}
		}
		else
		{
			Sudoku newSolution = new Sudoku(_sudoku);
			_parent.tell(new SudokuSupervisor.IterationFinishedMsg(newSolution));
		}
	}
}
