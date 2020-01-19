package sudoku;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Vector;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;


public class PlayerTest
{
	@ClassRule
	public static final TestKitJunitResource testKit = new TestKitJunitResource();

	@Test
	public void testRegisteringTables()
	{
		TestProbe<Teacher.Protocol> testTeacher = testKit.createTestProbe();

		final int thePlayerId = 9;
		final int expectedCnt = 9;

		ActorRef<Player.Protocol> thePlayer = testKit.spawn(
				Player.create(
						new Player.CreateMsg(thePlayerId, expectedCnt)
				),"thePlayer");

		final int excessiveId = expectedCnt;
		Vector<ActorRef<Table.Protocol>> tables = new Vector<>();
		for(int id = 0; id < expectedCnt + 1; id++)
			tables.add(testKit.spawn(Table.create(
					new Table.CreateMsg(id, new Position(id,0), expectedCnt)
			),"table-" + id));

		final int[] digits = {5,6,7,8,4,0,8,4,1};
		for(int id = 0; id < expectedCnt; id++)
		{
			thePlayer.tell(new Player.RegisterTableMsg(
					tables.get(id),
					id,
					digits[id],
					digits[id] == 0,
					testTeacher.getRef()
			));
			Teacher.RegisteredTableMsg response = (Teacher.RegisteredTableMsg) testTeacher.receiveMessage();
			assertEquals(id, response._tableId);
			assertEquals(true, response._isItDone);
		}

		final int[] tableIdsToTest = {0,1,2,3,4,5,6,7,8,9};
		thePlayer.tell(new Player.MemorisedDigitsRequestMsg(testTeacher.getRef(), tableIdsToTest));
		Teacher.MemorisedDigitsMsg reply = (Teacher.MemorisedDigitsMsg) testTeacher.receiveMessage();
		assertEquals(thePlayerId, reply._requestedPlayerId);
		for(int tableId = 0; tableId < 9; ++tableId)
		{
			assertEquals((int) digits[tableId], (int) reply._memorisedDigits.get(tableId).first);
			assertEquals(digits[tableId] == 0, reply._memorisedDigits.get(tableId).first == 0);
		}
		assertNull(reply._memorisedDigits.get(9));

		System.out.println("\n\t\t\t>>> IncorrectRegisterException expected <<< \n");
		thePlayer.tell(new Player.RegisterTableMsg(
				tables.get(excessiveId), excessiveId, 0, false, testTeacher.getRef()));
		Teacher.RegisteredTableMsg response = (Teacher.RegisteredTableMsg) testTeacher.receiveMessage();
		assertEquals(excessiveId, response._tableId);
		assertEquals(false, response._isItDone);
	}

	@Test
	public void testNegotiations()
	{
		// Prepare dummy Tables and dummy Teacher
		TestProbe<Teacher.Protocol> teacherDummy = testKit.createTestProbe();
		TestProbe<Table.Protocol> tableDummy = testKit.createTestProbe();
		Vector<TestProbe<Table.Protocol>> tableOtherDummies = new Vector<>();
		for(int i = 0; i < 8; i++)
			tableOtherDummies.add(testKit.createTestProbe());


		// Create Player to test
		ActorRef<Player.Protocol> thePlayer = testKit.spawn(
				Player.create(new Player.CreateMsg(0, 9)), "theTable1");


		// Register dummy Tables to the Player
		thePlayer.tell(new Player.RegisterTableMsg(
				tableDummy.getRef(), 0, 0, false, teacherDummy.getRef()));
		teacherDummy.receiveMessage();
		thePlayer.tell(new Player.RegisterTableMsg(
				tableOtherDummies.get(0).getRef(), 9, 5, true, teacherDummy.getRef()));
		teacherDummy.receiveMessage();
		for(int i = 1, j = 18; i < 8; i++, j+= 9)
		{
			thePlayer.tell(new Player.RegisterTableMsg(
					tableOtherDummies.get(i).getRef(), j, 0, false, teacherDummy.getRef()));
			teacherDummy.receiveMessage();
		}


		// Start "new iteration"
		thePlayer.tell(new Player.ResetMemoryMsg(teacherDummy.getRef()));
		Teacher.PlayerPerformedMemoryResetMsg response0 =
				(Teacher.PlayerPerformedMemoryResetMsg) teacherDummy.receiveMessage();
		assertEquals(0, response0._id);

		thePlayer.tell(new Player.ConsentToStartIterationMsg());
		Table.OfferMsg responseOffer = (Table.OfferMsg) tableDummy.receiveMessage();
		int respondedDigit = responseOffer._offeredDigit;
		assertNotEquals(5, respondedDigit);
		System.out.println("\n\t" +
				"Player offered " + respondedDigit + " with weight " + responseOffer._digitWeight);

		tableOtherDummies.get(0).expectNoMessage();
		for(TestProbe<Table.Protocol> tableOtherDummy : tableOtherDummies.subList(1, tableOtherDummies.size()))
		{
			responseOffer = (Table.OfferMsg) tableOtherDummy.receiveMessage();
			assertNotEquals(5, responseOffer._offeredDigit);
			assertEquals(respondedDigit, responseOffer._offeredDigit);
		}


		// Check Player's response for Table's request for additional info
		thePlayer.tell(new Player.AdditionalInfoRequestMsg(new int[]{5,6}, tableDummy.getRef(), 0));
		Table.AdditionalInfoMsg response = (Table.AdditionalInfoMsg) tableDummy.receiveMessage();
		assertTrue(Arrays.equals(response._digits, new int[]{5, 6}));
		assertTrue(Arrays.equals(response._collisions, new boolean[]{true, false}));
		assertTrue(Arrays.equals(response._weights, new float[]{0L, 0L}));


		// Send to a Player rejection of his offer
		thePlayer.tell(new Player.RejectOfferMsg(respondedDigit, tableDummy.getRef(), 0));
		Table.OfferMsg responseNewOffer = (Table.OfferMsg) tableDummy.receiveMessage();
		assertNotEquals(4, responseNewOffer._offeredDigit);
		assertNotEquals(5, responseNewOffer._offeredDigit);
		respondedDigit = responseNewOffer._offeredDigit;
		System.out.println("\n\t" +
				"Player offered next: " + respondedDigit + " with weight " + responseNewOffer._digitWeight + "\n");
		tableOtherDummies.get(0).expectNoMessage();
		for(TestProbe<Table.Protocol> tableOtherDummy : tableOtherDummies.subList(1, tableOtherDummies.size()))
			tableOtherDummy.expectNoMessage();


		// Send to a Player possible positive negotiations results with double plot twists from other tables
		thePlayer.tell(new Player.NegotiationsPositiveMsg(1, tableOtherDummies.get(1).getRef(), 18));
		Table.AcceptNegotiationsResultsMsg responseAccept_1 =
				(Table.AcceptNegotiationsResultsMsg) tableOtherDummies.get(1).receiveMessage();
		assertEquals(1, responseAccept_1._acceptedDigit);
		thePlayer.tell(new Player.NegotiationsPositiveMsg(1, tableOtherDummies.get(2).getRef(), 27));
		Table.WithdrawOfferMsg responseDecline =
				(Table.WithdrawOfferMsg) tableOtherDummies.get(2).receiveMessage();
		assertEquals(1, responseDecline._withdrawnDigit);
		thePlayer.tell(new Player.RejectOfferMsg(1, tableOtherDummies.get(1).getRef(), 18));
		tableOtherDummies.get(1).expectMessageClass(Table.OfferMsg.class);
		thePlayer.tell(new Player.NegotiationsPositiveMsg(1, tableOtherDummies.get(3).getRef(), 36));
		Table.AcceptNegotiationsResultsMsg responseAccept_2 =
				(Table.AcceptNegotiationsResultsMsg) tableOtherDummies.get(3).receiveMessage();
		assertEquals(1, responseAccept_2._acceptedDigit);
		thePlayer.tell(new Player.NegotiationsPositiveMsg(6, tableDummy.getRef(), 0));
		Table.AcceptNegotiationsResultsMsg responseAccept_3 =
				(Table.AcceptNegotiationsResultsMsg) tableDummy.receiveMessage();
		assertEquals(6, responseAccept_3._acceptedDigit);
	}
}
