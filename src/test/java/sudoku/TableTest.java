package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Vector;

import static junit.framework.TestCase.assertEquals;

public class TableTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringPlayers()
	{
		TestProbe<Table.RegisteredMsg> testProbe = testKit.createTestProbe();

		ActorRef<Table.Protocol> theTable = testKit.spawn(
				Table.create(new Table.CreateMsg(0, new Position(0,0))),"theTable");

		Vector<ActorRef<Player.Protocol>> players = new Vector<>();
		for(int id = 0; id < 4; id++)
			players.add(testKit.spawn(Player.create(new Player.CreateMsg(id, 9)
			),"player-" + id));

		for(int id = 0; id < 3; id++)
		{
			theTable.tell(new Table.RegisterPlayerMsg(players.get(id), id/*, testProbe.getRef()*/));
			Table.RegisteredMsg response = testProbe.receiveMessage();
			assertEquals(response._playerId, id);
			assertEquals(response._isItDone, true);
		}

		theTable.tell(new Table.RegisterPlayerMsg(players.get(3), 3/*, testProbe.getRef()*/));
		Table.RegisteredMsg response = testProbe.receiveMessage();
		assertEquals(response._playerId, 3);
		assertEquals(response._isItDone, false);
	}
}
