package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/** Agent that manages Timers (in parallel), responding to the Teacher. */
public class TimerManager extends AbstractBehavior<TimerManager.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message creating the agent. */
	public static class CreateMsg implements Protocol
	{
		public final ActorRef<Teacher.Protocol> _parent;
		public CreateMsg(ActorRef<Teacher.Protocol> parent)
		{
			this._parent = parent;
		}
	}

	/** Message from the Teacher waking him up after passing given time to check if its Tables are still alive. */
	public static class RemindToCheckTablesMsg implements Protocol, SharedProtocols.ValidationProtocol
	{
		public final int _waitMilliseconds;
		public final int[] _tableIds;
		public RemindToCheckTablesMsg(int waitMilliseconds, int[] tableIds)
		{
			this._waitMilliseconds = waitMilliseconds;
			this._tableIds = tableIds;
		}
	}

	/** Message from the Teacher announcing new big iteration. */
	public static class NewIterationStartedMsg implements Protocol, SharedProtocols.ValidationProtocol
	{
		public final int _waitMilliseconds;
		public NewIterationStartedMsg(int waitMilliseconds)
		{
			this._waitMilliseconds = waitMilliseconds;
		}
	}

	/** Message from the Timer announcing that given time has passed. */
	public static class TimePassedMsg implements Protocol, SharedProtocols.TimeMeasurementProtocol
	{
		public final int _timerId;
		public final int _type;
		public final ActorRef<Timer.Protocol> _replyTo;
		public TimePassedMsg(int timerId, ActorRef<Timer.Protocol> replyTo, int type)
		{
			this._timerId = timerId;
			this._replyTo = replyTo;
			this._type = type;
		}
	}


	/** Parent - the only agent TimeManager replies to. */
	private final ActorRef<Teacher.Protocol> _parent;

	/** Timers counter - it also represents the latest timerId. First timerId = 1. */
	private int _lastTimerId;

	private int _lastIterationId;

	/** Table Ids requested to be checked on the last message. */
	private int[] _latelyRequestedTableIds;

	/** Private constructor called only by CreateMsg. */
	private TimerManager(ActorContext<TimerManager.Protocol> context, CreateMsg msg)
	{
		super(context);
		this._parent = msg._parent;
		this._lastTimerId = 0;
		this._lastIterationId = 0;
	}

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return wrapped Behavior
	 */
	public static Behavior<TimerManager.Protocol> create(CreateMsg msg)
	{
		return Behaviors.setup(context -> new TimerManager(context, msg));
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	@Override
	public Receive<TimerManager.Protocol> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(RemindToCheckTablesMsg.class, this::onRemindToCheckTables)
				.onMessage(TimePassedMsg.class, this::onTimePassed)
				.onMessage(NewIterationStartedMsg.class, this::onNewIterationStarted)
				.build();
	}


	/**
	 * TimerManager when asked by the Teacher, creates an agent - Timer to measure some time.
	 * If the Teacher does not send another RemindToCheckTablesMsg until Timer ends clicking, it means that its Tables
	 * are not responding. Note that Teacher can send _tableIds == null, meaning that it last Table had finished.
	 * @param msg	request from the Teacher
	 * @return 		wrapped Behavior
	 */
	private Behavior<TimerManager.Protocol> onRemindToCheckTables(RemindToCheckTablesMsg msg)
	{
		_latelyRequestedTableIds = msg._tableIds;
		++_lastTimerId;
		if(msg._tableIds != null)
		{
			getContext().spawn(
					Timer.create(
							new Timer.CreateMsg(getContext().getSelf(), msg._waitMilliseconds, _lastTimerId, 1)
					),
					"timer-" + _lastTimerId
			);
		}
		return this;
	}

	/**
	 * TimerManager when asked by the Teacher, creates an agent - Timer to measure some time.
	 * If the Teacher does not send another NewIterationStartedMsg until Timer ends clicking, it means that there is
	 * some timeout.
	 * @param msg	request from the Teacher
	 * @return 		wrapped Behavior
	 */
	private Behavior<TimerManager.Protocol> onNewIterationStarted(NewIterationStartedMsg msg)
	{
		++_lastIterationId;
		getContext().spawn(
				Timer.create(
						new Timer.CreateMsg(getContext().getSelf(), msg._waitMilliseconds, _lastIterationId, 2)
				),
				"timer2-" + _lastIterationId
		);
		return this;
	}

	/**
	 * TimerManager is informed that certain time has passed. If in this very moment TimerManager did not received
	 * new reminder, it should reply with warning that Teacher's Tables are not responding. Timer, who sent this msg,
	 * is no longer needed - can be removed.
	 * @param msg	ping from the Timer
	 * @return 		wrapped Behavior
	 */
	private Behavior<TimerManager.Protocol> onTimePassed(TimePassedMsg msg)
	{
		if(msg._type == 1 && _lastTimerId == msg._timerId)	// if TimerManager did not received new reminder
			_parent.tell(new Teacher.TablesAreNotRespondingMsg(_latelyRequestedTableIds));

		if(msg._type == 2 && _lastIterationId == msg._timerId)	// if TimerManager did not received new reminder
			_parent.tell(new Teacher.IterationTimeoutMsg(_lastIterationId));


		getContext().stop(msg._replyTo);
		return this;
	}
}