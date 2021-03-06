package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Simple agent-guardian of the whole multi-agent-system solving Sudoku.
 * Existence required by Akka.
 * Parent of Teacher agent.
 */
public class SudokuSupervisor extends AbstractBehavior<SudokuSupervisor.Protocol>
{
	/** Abstract interface for all commands (messages) passed to the Agent. */
	public interface Protocol
	{}

	/**
	 * Message for controlled termination of the agent.
	 */
	public static final class TerminateMsg implements Protocol
	{
		final long _requestId;
		final ActorRef<String> _replyTo;

		TerminateMsg(long requestId, ActorRef<String> replyTo)
		{
			this._requestId = requestId;
			this._replyTo = replyTo;
		}
	}

	/** Message for making the Teacher crash. */
	public static class SimulateTeacherCrashMsg implements Protocol
	{
		final ActorRef<String> _replyTo;
		public SimulateTeacherCrashMsg(ActorRef<String> replyTo)
		{
			this._replyTo = replyTo;
		}
	}

	/** Message when Teacher is going to be restarted. */
	public static class TeacherWillRestartMsg implements Protocol
	{
		final String _msg;
		public TeacherWillRestartMsg(String msg)
		{
			this._msg = msg;
		}
	}

	/** Message received from the Teacher on every iteration's finish, containing new solution of the Sudoku riddle. */
	public static class IterationFinishedMsg implements Protocol
	{
		public final Sudoku _newSolution;
		public IterationFinishedMsg(Sudoku newSolution)
		{
			this._newSolution = newSolution;
		}
	}

	/** Sudoku riddle to be solved by the app. */
	private Sudoku _sudoku;
	/** Child Teacher agent. */
	private ActorRef<Teacher.Protocol> _teacher;
	/** Parent - agent for debugging when simulating Teacher's crash. */
	private ActorRef<String> _simulationParent;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return 		wrapped Behavior
	 */
	public static Behavior<Protocol> create()
	{
		return Behaviors.setup(SudokuSupervisor::new);
	}

	private SudokuSupervisor(ActorContext<Protocol> context)
	{
		super(context);
		context.getLog().info("SudokuSupervisor started");
		readSudoku();
		_teacher = context.spawn(
				Behaviors.supervise(
						Teacher.create(new Teacher.CreateMsg("TheOnlyTeacher", _sudoku, context.getSelf()))
				).onFailure(SupervisorStrategy.restart())
				, "teacher"
		);
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
				.onMessage(TerminateMsg.class, this::onTermination)
				.onMessage(SimulateTeacherCrashMsg.class, this::onSimulateTeacherCrash)
				.onMessage(TeacherWillRestartMsg.class, this::onTeacherWillRestart)
				.onMessage(IterationFinishedMsg.class, this::onIterationFinished)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping SudokuSupervisor agent.
	 * @return 		wrapped Behavior
	 */
	private SudokuSupervisor onPostStop()
	{
		getContext().getLog().info("SudokuSupervisor stopped.");
		return this;
	}

	/**
	 * Behaviour towards TerminateMsg message.
	 * Action performed on termination.
	 * @param terminateMsg	termination message
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onTermination(TerminateMsg terminateMsg)
	{
		getContext().getLog().info("SudokuSupervisor will be terminated.");
		terminateMsg._replyTo.tell("I will be terminated.");
		return Behaviors.stopped();
	}

	/**
	 * Behaviour towards SimulateTeacherCrashMsg message.
	 * Agent tells the Teacher to simulate crash.
	 * @param msg	 message simulating crash
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onSimulateTeacherCrash(SimulateTeacherCrashMsg msg)
	{
		_teacher.tell(new Teacher.SimulateCrashMsg());
		_simulationParent = msg._replyTo;
		return this;
	}

	/**
	 * Behaviour towards TeacherWillRestartMsg message.
	 * The Teacher responds just before restart.
	 * @param msg	 respond just before restart
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onTeacherWillRestart(TeacherWillRestartMsg msg)
	{
		_simulationParent.tell(msg._msg);
		return this;
	}

	/**
	 * As every iteration finishes, SudokuSupervisor saves new (iterated) solution on the hard drive.
	 * @param msg	message containing new solution represented by Sudoku object
	 * @return 		wrapped Behavior
	 */
	private Behavior<Protocol> onIterationFinished(IterationFinishedMsg msg)
	{
		// TODO Emil - zapisywanie kolejnej iteracji rozwiazania do pliku

		return this;
	}

	/**
	 * Action of reading _sudoku from file.
	 */
	private void readSudoku()
	{
		_sudoku = new Sudoku(3);	// TODO Emil - odczytywanie zadanego sudoku z pliku
	}
}
