package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/** Simple reactive agent that replies after some time. */
public class Timer extends AbstractBehavior<Timer.Protocol>
{
	/** Protocol interface for input messages. */
	public interface Protocol {}

	/** Message creating the agent, as well as starting the countdown. */
	public static class CreateMsg implements Protocol, SharedProtocols.TimeMeasurementProtocol
	{
		public final ActorRef<TimerManager.Protocol> _parent;
		public final int _milliseconds;
		public final int _timerId;
		public final int _type;
		public CreateMsg(ActorRef<TimerManager.Protocol> parent, int milliseconds, int timerId, int type)
		{
			this._parent = parent;
			this._milliseconds = milliseconds;
			this._timerId = timerId;
			this._type = type;
		}
	}

	/** Private constructor called only by CreateMsg. */
	private Timer(ActorContext<Protocol> context, CreateMsg msg)
	{
		super(context);
		countdown(msg._milliseconds);
		msg._parent.tell(new TimerManager.TimePassedMsg(msg._timerId, getContext().getSelf(), msg._type));
	}

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return wrapped Behavior
	 */
	public static Behavior<Protocol> create(CreateMsg msg)
	{
		return Behaviors.setup(context -> new Timer(context, msg));
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
				.build();
	}


	/** Timer waits for some time. */
	private void countdown(int milliseconds)
	{
		try
		{
			Thread.sleep(milliseconds);
		}
		catch (Exception e)
		{
			// if exception is catched, reply is going to be earlier.
			getContext().getLog().info("Exception thrown while sleeping.");
		}
	}
}