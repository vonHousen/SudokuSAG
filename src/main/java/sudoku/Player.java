package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;

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

	/** Protocol interface for messages during negotiations. */
	public interface NegotiationsProtocol extends Protocol {}

	/** Message for creating the Player. */
	public static class CreateMsg implements InitialisationProtocol
	{
		final int _playerId;
		final Position _playerPosition;
		final PlayerType _playerType;
		final int[] _digitVector;
		final boolean[] _digitMask;

		public CreateMsg(int playerId, Position playerPosition, PlayerType playerType, int[] digitVector, boolean[] digitMask)
		{
			this._playerId = playerId;
			this._playerPosition = playerPosition;
			this._playerType = playerType;
			this._digitVector = digitVector;
			this._digitMask = digitMask;
		}
	}

	/** Message for registering a Table. */
	public static class RegisterTableMsg implements InitialisationProtocol
	{
		final ActorRef<Table.Protocol> _tableToRegister;
		final Position _tablePos;
		final ActorRef<RegisteredMsg> _replyTo;
		public RegisterTableMsg(ActorRef<Table.Protocol> tableToRegister, Position tablePos, ActorRef<RegisteredMsg> replyTo)
		{
			this._tableToRegister = tableToRegister;
			this._tablePos = tablePos;
			this._replyTo = replyTo;
		}
	}

	/** Message sent out after registering a Table. */
	public static class RegisteredMsg implements InitialisationProtocol
	{
		final Position _tablePos;
		final boolean _isItDone;
		public RegisteredMsg(Position tablePos, boolean isItDone)
		{
			this._tablePos = tablePos;
			this._isItDone = isItDone;
		}
	}

	/** Abstract class for messages received from Table Agent during negotiations. */
	public static abstract class NegotiationsMsg implements NegotiationsProtocol
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


	/** Custom exception thrown when excessive Table is about to be registered to this Player */
	public static class IncorrectRegisterException extends RuntimeException
	{
		public IncorrectRegisterException(String msg)
		{
			super(msg);
		}
	}

	/** Defines if Player is a Row, a Column or a square Block */
	public enum PlayerType
	{
		COLUMN,
		ROW,
		BLOCK
	}

	/** Global ID of this Player */
	private final int _playerId;
	/** Data structure for storing Tables (agents registered to this Player) and internal sudoku digit indices */
	private final Map<Position, Pair<ActorRef<Table.Protocol>, Integer>> _tableIndex;
	/** Structure containing awards and current digit vector */
	private final Memory _memory;

	/**
	 * Add all keys to _tableIndex with correct index and actor reference set to null.
	 * @param origin	position of the player as an origin for creating all fields
	 * @param t			ROW/COLUMN/BLOCK of the player
	 * @param digitMask	data structure containing masks (if a field is hardcoded or not)
	 */
	private void fillTableIndex(Position origin, PlayerType t, boolean[] digitMask)
	{
		switch (t)
		{
			case COLUMN:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Position(origin.x, i), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
				break;
			case ROW:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Position(i, origin.y), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
				break;
			case BLOCK:
				for (int i = 0; i < digitMask.length; ++i)
				{
					if (!digitMask[i])
					{
						_tableIndex.put(new Position(origin.x + (i % digitMask.length), origin.y + (i / digitMask.length)), new Pair<ActorRef<Table.Protocol>, Integer>(null, i));
					}
				}
		}
	}

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
		_tableIndex = new HashMap<>();
		fillTableIndex(createMsg._playerPosition, createMsg._playerType, createMsg._digitMask);
		_memory = new Memory(createMsg._digitVector, createMsg._digitMask);
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
				.onMessage(AdditionalInfoRequestMsg.class, this::onAdditionalInfoRequest)
				.onMessage(RejectOfferMsg.class, this::onRejectOffer)
				.onMessage(NegotiationsPositiveMsg.class, this::onNegotiationsPositive)
				.onMessage(NegotiationsFinishedMsg.class, this::onNegotiationsFinished)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Registers new Table to this Player.
	 * It is expected that a table position is already registered and it is replaced with the new ActorRef.
	 * When a excessive Table is about to be registered, IncorrectRegisterException is thrown.
	 * Replies with RegisteredMsg.
	 * @param msg	message for registering new Table
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRegisterTable(RegisterTableMsg msg)
	{
		Integer index;
		if (_tableIndex.containsKey(msg._tablePos))
		{
			index = _tableIndex.get(msg._tablePos).second;
			_tableIndex.remove(msg._tablePos);
		}
		else
		{
			msg._replyTo.tell(new RegisteredMsg(msg._tablePos, false));
			throw new IncorrectRegisterException("Excessive Table cannot be registered");
		}

		_tableIndex.put(msg._tablePos, new Pair<ActorRef<Table.Protocol>, Integer>(msg._tableToRegister, index));
		msg._replyTo.tell(new RegisteredMsg(msg._tablePos, true));

		return this;
	}

	/**
	 * Weighs given offer(s) for given digits.
	 * During negotiations, Player will be asked for weighing other Players' offers. This is the action for that.
	 * Replies the Table with AdditionalInfoMsg.
	 * @param msg	request for additional info
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onAdditionalInfoRequest(AdditionalInfoRequestMsg msg)
	{
		// TODO backend

		/* 	Jak w dokumentacji, gracz dostaje wiadomość z requestem i odsyła AdditionalInfoMsg. Returna nie ruszaj.
			Nie pamiętam tylko co odsyła gdy jest konflikt. Miała być osobna wiadomość?

			odpowiadanie wygląda generalnie tak jak poniżej, tylko popraw nego nulla:)
			Zamiast msg._replyTo możesz też użyć referencji przechowywanej od momentu rejestracji.
		 */
		msg._replyTo.tell(new Table.AdditionalInfoMsg(null, getContext().getSelf(), _playerId));

		return this;
	}

	/**
	 * Player is informed that it's offer is rejected.
	 * Replies the Table with OfferMsg.
	 * @param msg	rejecting message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onRejectOffer(RejectOfferMsg msg)
	{
		// TODO backend

		/* 	Jak w dokumentacji. Powinien sobie zapisać że liczba jest konfliktowa i odesłać stosowne OfferMsg.
			Nie pamiętam tylko co odsyła gdy jest konflikt. Ale wydaje mi się że OfferMsg ze specjalną wagą może być?

			UWAGA: może dostać tą wiadomość także w sytuacji, gdy Gracz już zaakceptował jakąś ofertę. W takim wypadku
			powinien cofnąć blokadę tworzoną w czasie onNegotiationsPositive.
		 */
		msg._replyTo.tell(new Table.OfferMsg(0, 0, getContext().getSelf(), _playerId));

		return this;
	}

	/**
	 * Action taken when Player is informed of positive result of the negotiations.
	 * Replies the Table with AssessNegotiationsResultsMsg.
	 * @param msg	positive result of negotiations in a message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onNegotiationsPositive(NegotiationsPositiveMsg msg)
	{
		// TODO backend

		/* 	Wystarczy, że Gracz sobie sprawdzi czy dalej mu pasuje, i jeśli odsyła AssessNegotiationsResultsMsg,
			w którym zawiera czy akceptuje negocjacje czy nie. Pamiętaj, że jak akceptuje, to w TEJ funkcji natychmiast
			blokuje sobie tę cyfrę. Jak nie, to przesyła stosowne info w wiadomości powrotnej.
			W wiadomości przekazywana jest również cyfra na jaką się zgadza / nie zgadza - patrz funkcja u stolika.
		 */
		msg._replyTo.tell(new Table.AssessNegotiationsResultsMsg(true, 0, getContext().getSelf(), _playerId));

		return this;
	}

	/**
	 * Action taken when negotiations finished.
	 * My send WithdrawOfferMsg to other Tables.
	 * @param msg	message announcing final finish of the negotiations
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onNegotiationsFinished(NegotiationsFinishedMsg msg)
	{
		// TODO backend

		/* 	Generalnie to Gracz przyklepuje sobie blokadę cyfry - tzn że jest ona ostateczna i wpisana.

			UWAGA: dopiero teraz gracz przesyła do pozostałych stolików (jeśli trzeba oczywiście) wiadomość WithdrawOfferMsg.
			Chyba.:P
		 */
		ActorRef<Table.Protocol> table = null;
		table.tell(new Table.WithdrawOfferMsg(0, getContext().getSelf(), _playerId));


		return this;
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping Player agent.
	 * @return 		wrapped Behavior
	 */
	private Player onPostStop()
	{
		// getContext().getLog().info("Player {} stopped", _tableId); 	// left for debugging only
		return this;
	}
}
