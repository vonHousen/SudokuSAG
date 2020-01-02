package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

public class SudokuStartTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testSimpleStart()
	{
		TestProbe<Void> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Command> guardian = testKit.spawn(SudokuSupervisor.create(),"test1");
		testProbe.expectNoMessage();
	}

	@Test
	public void testSupervisorTermination()
	{
		TestProbe<String> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Command> guardian = testKit.spawn(SudokuSupervisor.create(),"test2");
		guardian.tell(new SudokuSupervisor.TerminateMsg(0L, testProbe.getRef()));
		testProbe.expectMessage("I will be terminated.");

		// below is just printout control
		try{Thread.sleep(250);} catch (Exception e){}
		System.out.println("======================================> Test finished");
	}
}
