package sudoku;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Simple agent-guardian of the whole multi-agent-system solving Sudoku.
 * Existence required by Akka.
 */
public class SudokuGuardian extends AbstractBehavior<SudokuGuardian.StartMsg>
{
	/**
	 * Message for starting the system.
	 */
	public static class StartMsg
	{
		final String name;

		public StartMsg(String name)
		{
			this.name = name;
		}
	}

	public static Behavior<StartMsg> create()
	{
		return Behaviors.setup(SudokuGuardian::new);
	}

	private SudokuGuardian(ActorContext<StartMsg> context)
	{
		super(context);
		context.getLog().info("SudokuGuardian started");
	}

	@Override
	public Receive<StartMsg> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(StartMsg.class, this::onStart)
				.build();
	}

	private Behavior<StartMsg> onStart(StartMsg command)
	{
		return this;
	}
}
