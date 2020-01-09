package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Vector;

import static junit.framework.TestCase.assertEquals;

public class PlayerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringTables()
	{
		TestProbe<Player.RegisteredMsg> testProbe = testKit.createTestProbe();

		ActorRef<Player.Protocol> thePlayer = testKit.spawn(
				Player.create(
						new Player.CreateMsg(
								0,
								9
								)
				),"thePlayer");

		final int expectedCnt = 9;
		final int excesiveId = expectedCnt;
		Vector<ActorRef<Table.Protocol>> tables = new Vector<>();
		for(int id = 0; id < expectedCnt + 1; id++)
			tables.add(testKit.spawn(Table.create(
					new Table.CreateMsg(id, new Position(id,0))
			),"table-" + id));

		for(int id = 0; id < expectedCnt; id++)
		{
			thePlayer.tell(new Player.RegisterTableMsg(
					tables.get(id), id, 0, false/*, testProbe.getRef()*/));
			Player.RegisteredMsg response = testProbe.receiveMessage();
			assertEquals(id, response._tableId);
			assertEquals(true, response._isItDone);
		}

		thePlayer.tell(new Player.RegisterTableMsg(
				tables.get(excesiveId), excesiveId, 0, false/*, testProbe.getRef()*/));
		Player.RegisteredMsg response = testProbe.receiveMessage();
		assertEquals(excesiveId, response._tableId);
		assertEquals(false, response._isItDone);
	}
}
