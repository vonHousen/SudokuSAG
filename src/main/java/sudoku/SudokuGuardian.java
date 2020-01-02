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
public class SudokuGuardian extends AbstractBehavior<SudokuGuardian.Start>
{
	/**
	 * Message for starting the system.
	 */
	public static class Start
	{
		final String name;

		public Start(String name)
		{
			this.name = name;
		}
	}

	public static Behavior<SudokuGuardian.Start> create()
	{
		return Behaviors.setup(SudokuGuardian::new);
	}

	private SudokuGuardian(ActorContext<SudokuGuardian.Start> context)
	{
		super(context);
		context.getLog().info("SudokuGuardian started");
	}

	@Override
	public Receive<SudokuGuardian.Start> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(SudokuGuardian.Start.class, this::onStart)
				.build();
	}

	private Behavior<SudokuGuardian.Start> onStart(SudokuGuardian.Start command)
	{
		return this;
	}
}
