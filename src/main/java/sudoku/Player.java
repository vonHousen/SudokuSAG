package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Playing agent, who actually learns to solve Sudoku.
 * A child of the Teacher agent.
 */
public class Player extends AbstractBehavior<Player.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Protocol interface for messages for initialisation strategy. */
	public interface InitialisationProtocol extends Protocol {}

	/** Message for creating the Player. */
	public static class CreateMsg implements InitialisationProtocol
	{
		final int _playerId;
		final int _sudokuSize;

		public CreateMsg(int playerId, int sudokuSize)
		{
			this._playerId = playerId;
			this._sudokuSize = sudokuSize;
		}
	}

	/** Message for registering a Table. Also it passes a piece of Sudoku for given Table's Position. */
	public static class RegisterTableMsg implements InitialisationProtocol, SharedProtocols.RegisteringProtocol
	{
		final ActorRef<Table.Protocol> _tableToRegister;
		final int _tableId;
		final int _digit;
		final boolean _mask;
		final ActorRef<Teacher.Protocol> _replyTo;
		public RegisterTableMsg(
				ActorRef<Table.Protocol> tableToRegister,
				int tableId,
				int digit,
				boolean mask,
				ActorRef<Teacher.Protocol> replyTo
		)
		{
			this._tableToRegister = tableToRegister;
			this._tableId = tableId;
			this._digit = digit;
			this._mask = mask;
			this._replyTo = replyTo;
		}
	}

	/** Abstract class for messages received from Table Agent during negotiations. */
	public static abstract class NegotiationsMsg implements Protocol, SharedProtocols.NegotiationsProtocol
	{
		public final ActorRef<Table.Protocol> _replyTo;
		public final int _tableId;

		protected NegotiationsMsg(ActorRef<Table.Protocol> replyTo, int tableId)
		{
			this._replyTo = replyTo;
			this._tableId = tableId;
		}
	}

	/** Message received from the Table, requesting for additional info about other stakeholder's(s') digit. */
	public static class AdditionalInfoRequestMsg extends NegotiationsMsg
	{
		public final int[] _otherDigits;

		public AdditionalInfoRequestMsg(int[] otherDigits, ActorRef<Table.Protocol> replyTo, int tableId)
		{
			super(replyTo, tableId);
			this._otherDigits = otherDigits;
		}
	}

	/** Message received when Player's offer is rejected, requesting for new offer. */
	public static class RejectOfferMsg extends NegotiationsMsg
	{
		public final int _rejectedDigit;

		public RejectOfferMsg(int rejectedDigit, ActorRef<Table.Protocol> replyTo, int tableId)
		{
			super(replyTo, tableId);
			this._rejectedDigit = rejectedDigit;
		}
	}

	/** Message received when the Table announces positive result of the negotiations. */
	public static class NegotiationsPositiveMsg extends NegotiationsMsg
	{
		public final int _approvedDigit;

		public NegotiationsPositiveMsg(int approvedDigit, ActorRef<Table.Protocol> replyTo, int tableId)
		{
			super(replyTo, tableId);
			this._approvedDigit = approvedDigit;
		}
	}

	/** Message received when the Table announces finish of the negotiations. */
	public static class NegotiationsFinishedMsg extends NegotiationsMsg
	{
		public final int _resultingDigit;

		public NegotiationsFinishedMsg(int resultingDigit, ActorRef<Table.Protocol> replyTo, int tableId)
		{
			super(replyTo, tableId);
			this._resultingDigit = resultingDigit;
		}
	}

	/**
	 * Message - request for memorised Digits and Masks for given Table IDs by the Player.
	 * Used for inspection performed by the Teacher and tests.
	 */
	public static class MemorisedDigitsRequestMsg implements Protocol, SharedProtocols.InspectionProtocol
	{
		public final ActorRef<Teacher.Protocol> _replyTo;
		public final int[] _tableIds;
		public MemorisedDigitsRequestMsg(ActorRef<Teacher.Protocol> replyTo, int[] tableIds)
		{
			this._replyTo = replyTo;
			this._tableIds = tableIds;
		}
	}

	/** Message commanding the agent to reset it's memory due to start of new iteration. */
	public static class ResetMemoryMsg implements Protocol, SharedProtocols.NewIterationProtocol
	{
		public final ActorRef<Teacher.Protocol> _replyTo;
		public ResetMemoryMsg(ActorRef<Teacher.Protocol> replyTo)
		{
			this._replyTo = replyTo;
		}
	}

	/** Message allowing the agent to start new iteration by sending new offers. */
	public static class ConsentToStartIterationMsg implements Protocol, SharedProtocols.NewIterationProtocol
	{}


	/** Custom exception thrown when excessive Table is about to be registered to this Player */
	public static class IncorrectRegisterException extends RuntimeException
	{
		final int _excessiveTableId;
		final int _crashingPlayerId;
		public IncorrectRegisterException(String msg, int excessiveTableId, int crashingPlayerId)
		{
			super(msg);
			this._excessiveTableId = excessiveTableId;
			this._crashingPlayerId = crashingPlayerId;
		}
	}

	/** Custom exception thrown when Table finishes negotiations with a digit different from what was offered. */
	public static class BadFinishException extends RuntimeException
	{
		final int _finishTableId;
		final int _crashingPlayerId;
		final int _playerDigit;
		final int _tableDigit;
		public BadFinishException(String msg, int finishTableId, int crashingPlayerId, int playerDigit, int tableDigit)
		{
			super(msg);
			this._finishTableId = finishTableId;
			this._crashingPlayerId = crashingPlayerId;
			this._playerDigit = playerDigit;
			this._tableDigit = tableDigit;
		}
	}

	/** Custom exception thrown when Player receives NegotiationsFinishedMsg with the same digit from more than one Table. */
	public static class DoubleFinishException extends RuntimeException
	{
		final int _finishTableId;
		final int _crashingPlayerId;
		final int _resultingDigit;
		public DoubleFinishException(String msg, int finishTableId, int crashingPlayerId, int resultingDigit)
		{
			super(msg);
			this._finishTableId = finishTableId;
			this._crashingPlayerId = crashingPlayerId;
			this._resultingDigit = resultingDigit;
		}
	}

	/** Custom exception thrown when a Player receives RejectOfferMsg with a different digit it offered. */
	public static class BadRejectionException extends RuntimeException
	{
		final int _rejectingTableId;
		final int _crashingPlayerId;
		final int _rejectedDigit;
		final int _playerDigit;
		public BadRejectionException(String msg, int rejectingTableId, int crashingPlayerId, int rejectedDigit, int playerDigit)
		{
			super(msg);
			this._rejectingTableId = rejectingTableId;
			this._crashingPlayerId = crashingPlayerId;
			this._rejectedDigit = rejectedDigit;
			this._playerDigit = playerDigit;
		}
	}

	/** Global ID of this Player */
	private final int _playerId;
	/** Structure containing awards and current digit vector */
	private final PlayerMemory _memory;
	/**
	 * Map from global Table id to internal index and Table reference.
	 * Data structure for storing Tables - agents registered to this Player.
	 */
	private final AgentMap<ActorRef<Table.Protocol>> _tables;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return 		wrapped Behavior
	 */
	public static Behavior<Player.Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Player(context, createMsg));
	}

	private Player(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		_playerId = createMsg._playerId;
		_memory = new PlayerMemory(createMsg._sudokuSize);
		_tables = new AgentMap<ActorRef<Table.Protocol>>(createMsg._sudokuSize);
		// context.getLog().info("Player {} created", _tableId);		// left for debugging only
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
				.onMessage(RegisterTableMsg.class, this::onRegisterTable)
				.onMessage(MemorisedDigitsRequestMsg.class, this::onMemorisedDigitsRequest)
				.onMessage(AdditionalInfoRequestMsg.class, this::onAdditionalInfoRequest)
				.onMessage(RejectOfferMsg.class, this::onRejectOffer)
				.onMessage(NegotiationsPositiveMsg.class, this::onNegotiationsPositive)
				.onMessage(NegotiationsFinishedMsg.class, this::onNegotiationsFinished)
				.onMessage(ResetMemoryMsg.class, this::onResetMemory)
				.onMessage(ConsentToStartIterationMsg.class, this::onConsentToStartIteration)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Registers new Table to this Player.
	 * It is expected that a table position is already registered and it is replaced with the new ActorRef.
	 * When a excessive Table is about to be registered, IncorrectRegisterException is thrown.
	 *
	 * Also it assigns a piece of Sudoku for given Table's Position to the Player's memory (_memory.setField(...)).
	 *
	 * Replies with RegisteredMsg.
	 * @param msg	message for registering new Table
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterTable(RegisterTableMsg msg)
	{
		if (_tables.isFull())
		{
			msg._replyTo.tell(new Teacher.RegisteredTableMsg(msg._tableId, false));
			throw new IncorrectRegisterException(
					"Excessive Table (tableId: " + msg._tableId + ") " +
							"cannot be registered to the Player (playerId: " + _playerId + ").",
					msg._tableId,
					_playerId
			);
		}
		_tables.register(msg._tableId, msg._tableToRegister);
		_memory.setField(_tables.getIndex(msg._tableId), msg._digit, msg._mask);
		msg._replyTo.tell(new Teacher.RegisteredTableMsg(msg._tableId, true));

		return this;
	}

	/**
	 * During inspection, replies with all memorised Digits and Masks.
	 * Replies with MemorisedDigitsMsg.
	 * @param msg	inspection request
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onMemorisedDigitsRequest(MemorisedDigitsRequestMsg msg)
	{
		int localIndex, digit;
		boolean mask;
		Teacher.MemorisedDigitsMsg replyMsg = new Teacher.MemorisedDigitsMsg(new HashMap<>(), _playerId);

		for(int globalId : msg._tableIds)
		{
			localIndex = _tables.getIndex(globalId);
			if(localIndex < 0)
				continue;

			digit = _memory.getDigit(localIndex);
			mask = _memory.getMask(localIndex);
			replyMsg._memorisedDigits.put(globalId, new Pair<>(digit, mask));
		}
		msg._replyTo.tell(replyMsg);

		return this;
	}

	/**
	 * Weighs given offer(s) for given digits.
	 * During negotiations, Player will be asked for weighing other Players' offers. This is the action for that.
	 * Replies the Table with AdditionalInfoMsg.
	 * @param msg	request for additional info
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onAdditionalInfoRequest(AdditionalInfoRequestMsg msg) // specify
	{
		final int length = msg._otherDigits.length;
		float[] weights = new float[length];
		boolean[] collisions = new boolean[length];
		final int index = _tables.getIndex(msg._tableId);
		for (int i = 0; i < length; ++i)
		{
			final int digit = msg._otherDigits[i];
			weights[i] = _memory.getAward(index, digit);
			collisions[i] = _memory.getCollision(index, digit);
		}
		msg._replyTo.tell(
				new Table.AdditionalInfoMsg(msg._otherDigits, weights, collisions, getContext().getSelf(), _playerId)
		);

		return this;
	}

	/**
	 * Send offer message to a Table.
	 * Method tries to choose the best offer possible (with the highest weight) for this specific Table.
	 * @param tableIndex	internal index of Table, to which the message is going to be sent
	 */
	private void sendBestOffer(int tableIndex)
	{
		final int sudokuSize = _memory.getSudokuSize();
		boolean offerSent = false;
		for (int p = 0; p < sudokuSize; ++p)
		{
			final int digit = _memory.getDigitPriority(tableIndex, p);
			if (!_memory.getCollision(tableIndex, digit)) // If the digit doesn't collide
			{
				_memory.setDigit(tableIndex, digit);
				final ActorRef<Table.Protocol> tempTableRef = _tables.getAgent(tableIndex);
				tempTableRef.tell(new Table.OfferMsg(digit,
						_memory.getAward(tableIndex, digit),
						getContext().getSelf(), _playerId));
				offerSent = true;
				break; // Do not send any more offers to this Table
			}
		}
		if (!offerSent) // Couldn't offer any digit
		{
			_memory.setDigit(tableIndex, 0);
			final ActorRef<Table.Protocol> tempTableRef = _tables.getAgent(tableIndex);
			// Send offer with a special value (zero)
			tempTableRef.tell(new Table.OfferMsg(0, 0L, getContext().getSelf(), _playerId));
		}
	}

	/**
	 * Player is informed that theirs offer is rejected.
	 * Replies the Table with OfferMsg.
	 * @param msg	rejecting message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRejectOffer(RejectOfferMsg msg) // cancel
	{
		final int tableIndex = _tables.getIndex(msg._tableId);
		final int rejectedDigit = msg._rejectedDigit;
		{
			final int myDigit = _memory.getDigit(tableIndex);
			if (rejectedDigit != myDigit)
			{
				throw new BadRejectionException("Table rejected different digit than Player offered.",
						msg._tableId, _playerId, rejectedDigit, myDigit);
			}
		}
		_memory.setAccepted(tableIndex, false);
		_memory.setCollision(tableIndex, rejectedDigit);
		sendBestOffer(tableIndex);

		return this;
	}

	/**
	 * Action taken when Player is informed of positive result of the negotiations.
	 * Replies the Table with AcceptNegotiationsResultsMsg.
	 * @param msg	positive result of negotiations in a message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onNegotiationsPositive(NegotiationsPositiveMsg msg) // winner
	{
		final int approvedDigit = msg._approvedDigit;
		final int tableIndex = _tables.getIndex(msg._tableId);
		final ActorRef<Table.Protocol> tableRef = _tables.getAgent(tableIndex);
		// Check if the digit was already accepted on a different Table
		if (_memory.alreadyAccepted(approvedDigit))
		{
			_memory.setCollision(tableIndex, approvedDigit);
			tableRef.tell(new Table.WithdrawOfferMsg(approvedDigit, getContext().getSelf(), _playerId));
		}
		else
		{
			_memory.setDigit(tableIndex, approvedDigit);
			_memory.setAccepted(tableIndex, true);
			tableRef.tell(new Table.AcceptNegotiationsResultsMsg(approvedDigit, getContext().getSelf(), _playerId));
		}

		return this;
	}

	/**
	 * Action taken when negotiations finished.
	 * My send WithdrawOfferMsg to other Tables.
	 * @param msg	message announcing final finish of the negotiations
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onNegotiationsFinished(NegotiationsFinishedMsg msg) // inserted
	{
		final int index = _tables.getIndex(msg._tableId);
		final int resultingDigit = msg._resultingDigit;
		if (resultingDigit != 0) // Finished with non-empty field
		{
			final int myDigit = _memory.getDigit(index);
			if (myDigit != resultingDigit)
			{
				throw new BadFinishException("Player finished negotiations with a different digit than Table.",
						msg._tableId, _playerId, myDigit, resultingDigit);
			}
			final ArrayList<Integer> tableIndices = _memory.finishNegotiations(index);
			_memory.setDigitColliding(myDigit);
			for (Integer n : tableIndices)
			{
				if (_memory.isFinished(n)) // Digit was already chosen (permanently) on another Table
				{
					throw new Player.DoubleFinishException("Digit was already inserted somewhere else.",
							msg._tableId, _playerId, myDigit);
				}
			}
		}
		else
		{
			_memory.setDigit(index, 0);
			_memory.finish(index);
		}

		return this;
	}

	/**
	 * Player resets it's memory to get ready for new iteration.
	 * @param msg	message from the Teacher
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onResetMemory(ResetMemoryMsg msg)
	{
		_memory.reset();
		msg._replyTo.tell(new Teacher.PlayerPerformedMemoryResetMsg(_playerId));
		return this;
	}

	/**
	 * Player receives permission to start new iteration.
	 * It chooses the best offers it can make and sends them to appropriate tables.
	 * @param msg	permission from the Teacher
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onConsentToStartIteration(ConsentToStartIterationMsg msg)
	{
		_memory.prioritizeTables();
		final int sudokuSize = _memory.getSudokuSize();
		for (int i = 0; i < sudokuSize; ++i) // For each Table
		{
			final int tableIndex = _memory.getTablePriority(i);
			if (!_memory.getMask(tableIndex)) // If the field is not hard-coded
			{
				sendBestOffer(tableIndex);
			}
		}

		return this;
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping Player agent.
	 * @return 		wrapped Behavior
	 */
	private Player onPostStop()
	{
		//getContext().getLog().info("Player {} stopped", _playerId); 	// left for debugging only
		return this;
	}
}
