package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

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
		public CreateMsg(int tableId, Position tablePos, int sudokuSize)
		{
			this._tableId = tableId;
			this._tablePos = tablePos;
			this._sudokuSize = sudokuSize;
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
		public final int _digitWeight;

		public OfferMsg(int offeredDigit, int digitWeight, ActorRef<Player.Protocol> replyTo, int playerId)
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

	/** Message telling if the Player accepted negotiations results or not. */
	public static class AssessNegotiationsResultsMsg extends NegotiationsMsg
	{
		public final boolean _didAccept;
		public final int _assessedDigit;

		public AssessNegotiationsResultsMsg(boolean didAccept, int assessedDigit, ActorRef<Player.Protocol> replyTo, int playerId)
		{
			super(replyTo, playerId);
			this._didAccept = didAccept;
			this._assessedDigit = assessedDigit;
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
		_players = new AgentMap<ActorRef<Player.Protocol>>(3);
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
				.onMessage(AssessNegotiationsResultsMsg.class, this::onAssessNegotiationsResults)
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
	 * Receives new offer from registered Player.
	 * This action formally starts the negotiations.
	 * May reply all Players with RejectOfferMsg.
	 * @param msg	message representing Player's offer of digit to be inputted
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onOffer(OfferMsg msg) // offer
	{
		// TODO backend

		/* 	Stolik powinien zebrać oferty (potem ogarniemy timeout) - powinien je liczyć. Jak dostanie wszystkie, to
			powinien rozesłać AdditionalInfoRequestMsg. Pamiętaj, że stolik nie może czekać aktywnie. Pamiętaj że może
			dostać tę wiadomość jako update starej oferty.
			Return zostaw tak jak jest.


			odpowiadanie wygląda generalnie tak jak poniżej, tylko popraw nego nulla:)
			player inicjujesz referencją przechowywaną od momentu rejestracji Graczy.

		 */

		final int index = _players.getIndex(msg._playerId);
		ActorRef<Player.Protocol> player = _players.getAgent(index);
		final int digit = msg._offeredDigit;

		if (digit == 0) // Player cannot offer anything - Table must finish negotiations immediately
		{
			_memory.setBestOffer(0);
			for (int i = 0; i < 3; ++i)
			{
				final ActorRef<Player.Protocol> tempPlayerRef = _players.getAgent(i);
				if (tempPlayerRef != player) // Don't send message to the Player who wanted to quit negotiations
				{
					tempPlayerRef.tell(new Player.NegotiationsFinishedMsg(0, getContext().getSelf(), _tableId));
				}
			}
			return this;
		}

		if (_memory.isDenied(digit)) // Digit causes conflict for some Player
		{
			player.tell(new Player.RejectOfferMsg(digit, getContext().getSelf(), _tableId));
			return this;
		}

		// Add offer to memory
		_memory.setOffer(index, digit, msg._digitWeight);

		if (_memory.getOfferCount() == 3) // Gathered offers from all 3 players
		{
			for (int i = 0; i < 3; ++i)
			{
				// Check if the Table needs more information from Players
				int[] unknownDigits = _memory.getUnknownDigits(i);
				if (unknownDigits.length > 0)
				{
					// Ask Player for more information
					final ActorRef<Player.Protocol> tempPlayerRef = _players.getAgent(i);
					tempPlayerRef.tell(new Player.AdditionalInfoRequestMsg(unknownDigits, getContext().getSelf(), _tableId));
				}
				else
				{
					// Table knows all the information it needs from Player #i
					_memory.setSpecifyFlag(i, true);
				}
			}
		}

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
		// TODO backend

		/* 	Stolik powinien zebrać wagi i ogarnąć co robić dalej. Jak dostanie info o konflikcie to wydaje mi się że
			nie powinien nawet czekać na resztę opinii tylko wysłać komu trzeba wiadomość o wycofaniu oferty
			(RejectOfferMsg) i poprosić spóźnialskich o nowe opinie.
		 */
		ActorRef<Player.Protocol> player = null;
		player.tell(new Player.RejectOfferMsg(0, getContext().getSelf(), _tableId));

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
		// TODO backend

		/* 	Stolik powinien poprawić swoje dane i czekać (ale nie aktywnie!) na kolejny OfferMsg. Nie przewiduję tu
			odpowiadania komukolwiek, bo po co?
		 */

		return this;
	}

	/**
	 * Table collects (optimistically) all accepting messages and finishes negotiations.
	 * Table may also get declining message, what results in continuing the negotiations.
	 * To prevent synchronization issues Table must check if msg._assessedDigit is up to date with Table's one.
	 * May reply to all Players with RejectOfferMsg or NegotiationsFinishedMsg.
	 * @param msg	message with Player's acceptance / decline of present negotiations results.
	 * @return		wrapped Behavior
	 */
	private Behavior<Protocol> onAssessNegotiationsResults(AssessNegotiationsResultsMsg msg) // accept
	{
		// TODO backend

		/* 	Tak jak w dokumentacji.
		 */


		ActorRef<Player.Protocol> player = null;
		player.tell(new Player.RejectOfferMsg(0, getContext().getSelf(), _tableId));
		player.tell(new Player.NegotiationsFinishedMsg(0, getContext().getSelf(), _tableId));

		return this;
	}
}
