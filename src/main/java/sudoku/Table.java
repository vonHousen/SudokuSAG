package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;

/**
 * Table agent, who moderates negotiations between 3 players for choosing the best number in the Sudoku cell.
 * A child of the Teacher agent.
 */
public class Table extends AbstractBehavior<Table.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Protocol interface for messages for initialisation strategy */
	public interface InitialisationProtocol extends Protocol {}

	/** Message for creating the Table. */
	public static class CreateMsg implements InitialisationProtocol
	{
		final int _tableId;
		final Position _tablePos;
		final int _sudokuSize;
		final ActorRef<Teacher.Protocol> _replyTo;
		public CreateMsg(int tableId, Position tablePos, int sudokuSize, ActorRef<Teacher.Protocol> replyTo)
		{
			this._tableId = tableId;
			this._tablePos = tablePos;
			this._sudokuSize = sudokuSize;
			this._replyTo = replyTo;
		}
	}

	/** Message for registering a Player. */
	public static class RegisterPlayerMsg implements InitialisationProtocol, SharedProtocols.RegisteringProtocol
	{
		final ActorRef<Player.Protocol> _playerToRegister;
		final int _playerId;
		final ActorRef<Teacher.Protocol> _replyTo;
		public RegisterPlayerMsg(
				ActorRef<Player.Protocol> playerToRegister,
				int playerId,
				ActorRef<Teacher.Protocol> replyTo
		)
		{
			this._playerToRegister = playerToRegister;
			this._playerId = playerId;
			this._replyTo = replyTo;
		}
	}

	/** Abstract class for messages received from the Player during negotiations. */
	public static abstract class NegotiationsMsg implements Protocol, SharedProtocols.NegotiationsProtocol
	{
		public final ActorRef<Player.Protocol> _replyTo;
		public final int _playerId;

		protected NegotiationsMsg(ActorRef<Player.Protocol> replyTo, int playerId)
		{
			this._replyTo = replyTo;
			this._playerId = playerId;
		}
	}

	/** Message received from the Player, consisting of it's subjectively the best digit to be inserted */
	public static class OfferMsg extends NegotiationsMsg
	{
		public final int _offeredDigit;
		public final float _digitWeight;

		public OfferMsg(int offeredDigit, float digitWeight, ActorRef<Player.Protocol> replyTo, int playerId)
		{
			super(replyTo, playerId);
			this._offeredDigit = offeredDigit;
			this._digitWeight = digitWeight;
		}
	}

	/** Message received from the Player, consisting of it's subjective weights of requested digits. */
	public static class AdditionalInfoMsg extends NegotiationsMsg
	{
		public final int[] _digits;
		public final float[] _weights;
		public final boolean[] _collisions;

		public AdditionalInfoMsg(int[] digits, float[] weights, boolean[] collisions, ActorRef<Player.Protocol> replyTo, int playerId)
		{
			super(replyTo, playerId);
			this._digits = digits;
			this._weights = weights;
			this._collisions = collisions;
		}
	}

	/** Message received when the Player withdraws it's present offer. */
	public static class WithdrawOfferMsg extends NegotiationsMsg
	{
		public final int _withdrawnDigit;

		public WithdrawOfferMsg(int withdrawnDigit, ActorRef<Player.Protocol> replyTo, int playerId)
		{
			super(replyTo, playerId);
			this._withdrawnDigit = withdrawnDigit;
		}
	}

	/** Message telling that the Player accepted negotiations results. */
	public static class AcceptNegotiationsResultsMsg extends NegotiationsMsg
	{
		public final int _acceptedDigit;

		public AcceptNegotiationsResultsMsg(int acceptedDigit, ActorRef<Player.Protocol> replyTo, int playerId)
		{
			super(replyTo, playerId);
			this._acceptedDigit = acceptedDigit;
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


	/** Custom exception thrown when 4th Player is about to be registered to this table. */
	public static class IncorrectRegisterException extends RuntimeException
	{
		public IncorrectRegisterException(String msg)
		{
			super(msg);
		}
	}

	/** Global ID of the Table */
	private final int _tableId;
	/** Global position of the Table */
	private final Position _tablePos;
	/** Structure containing current state of Table */
	private final TableMemory _memory;
	/**
	 * Map from global Player id to internal index and Player reference
	 * Data structure for storing Players - agents registered to this Table.
	 */
	private AgentMap<ActorRef<Player.Protocol>> _players;
	/** Reference to Table's parent - the Teacher */
	private final ActorRef<Teacher.Protocol> _parent;


	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @param createMsg 	message initialising the start of the agent
	 * @return 				wrapped Behavior
	 */
	public static Behavior<Protocol> create(CreateMsg createMsg)
	{
		return Behaviors.setup(context -> new Table(context, createMsg));
	}

	private Table(ActorContext<Protocol> context, CreateMsg createMsg)
	{
		super(context);
		_tableId = createMsg._tableId;
		_tablePos = createMsg._tablePos;
		_memory = new TableMemory(createMsg._sudokuSize);
		_players = new AgentMap<>(3);
		_parent = createMsg._replyTo;
		// context.getLog().info("Table {} created", _TableId);			// left for debugging only
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return	wrapped Behavior
	 */
	@Override
	public Receive<Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(RegisterPlayerMsg.class, this::onRegisterPlayer)
				.onMessage(OfferMsg.class, this::onOffer)
				.onMessage(AdditionalInfoMsg.class, this::onAdditionalInfo)
				.onMessage(WithdrawOfferMsg.class, this::onWithdrawOffer)
				.onMessage(AcceptNegotiationsResultsMsg.class, this::onAcceptNegotiationsResults)
				.onMessage(ResetMemoryMsg.class, this::onResetMemory)
				.build();
	}

	/**
	 * Registers new Player to this Table.
	 * When a playerId is already registered, it is replaced with the new ActorRef.
	 * When a 4th Player is about to be registered, IncorrectRegisterException is thrown.
	 * Replies with RegisteredMsg.
	 * @param msg	message for registering new Player
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterPlayer(RegisterPlayerMsg msg)
	{
		if (_players.isFull())
		{
			msg._replyTo.tell(new Teacher.RegisteredPlayerMsg(msg._playerId, false));
			throw new IncorrectRegisterException("4th Player cannot be registered");
		}
		_players.register(msg._playerId, msg._playerToRegister);
		msg._replyTo.tell(new Teacher.RegisteredPlayerMsg(msg._playerId, true));

		return this;
	}

	/**
	 * Table tries to evaluate the best offer if gathered enough information.
	 * If there's not enough information, Table asks Players for it and waits for response.
	 * When the best offer is chosen, Table informs all the Players about this.
	 */
	private void attemptBestOffer()
	{
		if (_memory.getOfferCount() == 3) // Gathered offers from all 3 Players
		{
			for (int i = 0; i < 3; ++i)
			{
				if (!_memory.getSpecifyFlag(i)) // Table might need more information from Player #i
				{
					// If not awaiting for message from Player #i (or the message is going to be outdated)
					final int[] unknownDigits = _memory.getUnknownDigits(i);
					// Check if the Table truly needs more information from Player #i
					if (unknownDigits.length > 0)
					{
						// Ask Player #i for more information
						final ActorRef<Player.Protocol> tempPlayerRef = _players.getAgent(i);
						tempPlayerRef.tell(new Player.AdditionalInfoRequestMsg(unknownDigits, getContext().getSelf(), _tableId));
						_memory.incrementRequestCount(i);
					}
					// Table already requested or knows the information it needs from Player #i
					_memory.setSpecifyFlag(i, true);
				}
			}
			// Table knows all the information from all Players (no conflicts)
			if (_memory.allSpecifyFlagTrue() && _memory.noRequestsPending())
			{
				// So it can choose the best offer
				_memory.chooseBestOffer();
				final int bestDigit = _memory.getBestOffer();
				// And tell every Player about it
				for (int i = 0; i < 3; ++i)
				{
					final ActorRef<Player.Protocol> tempPlayerRef = _players.getAgent(i);
					tempPlayerRef.tell(new Player.NegotiationsPositiveMsg(bestDigit, getContext().getSelf(), _tableId));
				}
			}
		}
	}

	/**
	 * Ends negotiations irrevocably.
	 * Sends TableFinishedNegotiationsMsg to the Teacher reporting finish of the negotiations.
	 */
	private void quitNegotiations()
	{
		int digitSolution = _memory.getBestOffer();
		for (int i = 0; i < 3; ++i)
		{
			_players.getAgent(i).tell(new Player.NegotiationsFinishedMsg(
					digitSolution, getContext().getSelf(), _tableId));
		}
		_parent.tell(new Teacher.TableFinishedNegotiationsMsg(digitSolution, _tablePos, _tableId));
	}

	/**
	 * Register a digit as colliding (denied) and inform proper Players about this fact.
	 * @param digitColliding	digit to be withdrawn
	 */
	private void withdrawAndInform(int digitColliding)
	{
		_memory.setBestOffer(0);
		_memory.resetAcceptanceCount();
		final ArrayList<Integer> playerIndices = _memory.withdrawDigit(digitColliding);
		for (Integer n : playerIndices)
		{
			final ActorRef<Player.Protocol> tempPlayerRef = _players.getAgent(n);
			tempPlayerRef.tell(new Player.RejectOfferMsg(digitColliding, getContext().getSelf(), _tableId));
		}
	}

	/**
	 * Receives new offer from registered Player.
	 * This action formally starts the negotiations.
	 * May reply all Players with RejectOfferMsg.
	 * @param msg	message representing Player's offer of digit to be inputted
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onOffer(OfferMsg msg) // offer
	{
		final int index = _players.getIndex(msg._playerId);
		final ActorRef<Player.Protocol> player = _players.getAgent(index);
		final int digit = msg._offeredDigit;

		if (digit == 0) // Player cannot offer anything - Table must finish negotiations immediately
		{
			_memory.setBestOffer(0);
			quitNegotiations();
			return this;
		}

		if (_memory.isDenied(digit)) // Digit causes conflict for some Player
		{
			player.tell(new Player.RejectOfferMsg(digit, getContext().getSelf(), _tableId));
			return this;
		}

		// Add offer to memory
		_memory.setOffer(index, digit, msg._digitWeight);

		// Try choosing the best offer
		attemptBestOffer();

		return this;
	}

	/**
	 * Receives requested weighs for given digits.
	 * During this action Table analyses if there is a conflict or not and takes appropriate actions.
	 * To prevent synchronization issues Table must check if weighed digit is up to date with Tables's one.
	 * May reply all Players with RejectOfferMsg.
	 * @param msg	message representing Player's requested weighs for given digits
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onAdditionalInfo(AdditionalInfoMsg msg) // specified
	{
		final int index = _players.getIndex(msg._playerId);

		for (int i = 0; i < msg._digits.length; ++i)
		{
			if (msg._collisions[i]) // Digit causes collision for the sender Player
			{
				withdrawAndInform(msg._digits[i]);
			}
			else
			{
				_memory.setWeight(index, msg._digits[i], msg._weights[i]);
			}
		}
		_memory.decrementRequestCount(index);

		// Try choosing the best offer
		attemptBestOffer();

		return this;
	}

	/**
	 * Action on the Player's present offer withdrawal.
	 * No reply.
	 * @param msg	message with layer's withdrawal along with withdrawn digit
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onWithdrawOffer(WithdrawOfferMsg msg) // deny
	{
		withdrawAndInform(msg._withdrawnDigit);

		return this;
	}

	/**
	 * Table collects accepting messages and finishes negotiations.
	 * Table may also get declining message, what results in continuing the negotiations.
	 * To prevent synchronization issues Table must check if msg._acceptedDigit is up to date with Table's one.
	 * May reply to all Players with RejectOfferMsg or NegotiationsFinishedMsg.
	 * @param msg	message with Player's acceptance / decline of present negotiations results.
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onAcceptNegotiationsResults(AcceptNegotiationsResultsMsg msg) // accept
	{
		if (msg._acceptedDigit == _memory.getBestOffer())
		{
			_memory.incrementAcceptanceCount();
			if (_memory.allAcceptances())
			{
				quitNegotiations();
			}
		}

		return this;
	}

	/**
	 * Table resets it's memory to get ready for new iteration.
	 * @param msg	message from the Teacher
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onResetMemory(ResetMemoryMsg msg)
	{
		_memory.reset();
		msg._replyTo.tell(new Teacher.TablePerformedMemoryResetMsg(_tableId));
		return this;
	}
}
