package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

public class TeacherTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testCrashingTeacher()
	{
		TestProbe<String> testProbe = testKit.createTestProbe();
		ActorRef<SudokuSupervisor.Command> guardian = testKit.spawn(SudokuSupervisor.create(),"test3");
		guardian.tell(new SudokuSupervisor.SimulateTeacherCrashMsg(testProbe.getRef()));
		String response = testProbe.receiveMessage();
		assertEquals("I will be restarted.", response);

		// below is just printout control
		try{Thread.sleep(1000);} catch (Exception e){}
		System.out.println("======================================> Test finished");
	}
}
