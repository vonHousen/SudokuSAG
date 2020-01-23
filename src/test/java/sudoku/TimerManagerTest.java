package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

public class TimerManagerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void simpleTest()
	{
		TestProbe<Teacher.Protocol> dummyTeacher = testKit.createTestProbe();

		ActorRef<TimerManager.Protocol> theTimer = testKit.spawn(
				TimerManager.create(new TimerManager.CreateMsg(dummyTeacher.getRef())), "timer1");
		theTimer.tell(new TimerManager.RemindToCheckTablesMsg(500, new int[]{0,1}));
		dummyTeacher.expectMessageClass(Teacher.CheckTblMsg.class);
	}
}
