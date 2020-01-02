package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import org.junit.ClassRule;
import org.junit.Test;

public class SudokuStartTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testSimpleStart()
	{
		TestProbe<SudokuGuardian.StartMsg> testProbe = testKit.createTestProbe();
		ActorRef<SudokuGuardian.StartMsg> guardian = testKit.spawn(SudokuGuardian.create(),"starter");
		testProbe.expectNoMessage();
	}
}
