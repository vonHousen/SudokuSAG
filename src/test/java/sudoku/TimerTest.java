package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

public class TimerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void simpleTest()
	{
		TestProbe<Teacher.Protocol> dummyTeacher = testKit.createTestProbe();

		ActorRef<Timer.Protocol> theTimer = testKit.spawn(Timer.create(), "timer1");
		theTimer.tell(new Timer.RemindToCheckTablesMsg(dummyTeacher.getRef(), 1, new int[]{0,1}));
		dummyTeacher.expectMessageClass(Teacher.CheckTblMsg.class);
	}
}
