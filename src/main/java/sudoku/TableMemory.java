package sudoku;

import akka.actor.typed.javadsl.ActorContext;
import jdk.nashorn.internal.codegen.FunctionSignature;
import scala.Array;

import java.util.*;

public class TableMemory
{
    /** Values of digits offered by Players. Indices represent Player. Zeros indicate currently unused slots. */
    private final int[] _offers;
    /** Structure of unique offers given by Players. */
    private final ArrayList<TableOffer> _uniqueOffers;
    /** Flags indicating that all _weightFlags for a specific Player are set to true for all the _uniqueOffers.
     * It means that the Table knows all the information it needs from that Player. */
    private final boolean[] _specifyFlags;
    /** Flag indicating that Table awaits for a message from a certain Player. */
    private final boolean[] _requestPending;
    /** Mask of forbidden digits. True means, the digit causes a conflict for some Player. */
    private final boolean[] _deniedMask;
    /** Number of offers currently proposed by Players (offers don't have to be unique). */
    private int _offerCount;
    /** Number of acceptance messages received from Players. Three messages guarantee insertion of _bestOffer digit. */
    private int _acceptanceCount;
    /** The offer (digit) chosen after negotiations with Players. Zero means, the offer was not chosen yet. */
    private int _bestOffer;

    public TableMemory(int sudokuSize)
    {
        this._offers = new int[3]; // By default initialized to 0
        this._uniqueOffers = new ArrayList<>();
        this._specifyFlags = new boolean[3]; // By default initialized to false
        this._requestPending = new boolean[3]; // By default initialized to false
        this._deniedMask = new boolean[sudokuSize]; // By default initialized to false
        this._offerCount = 0;
        this._acceptanceCount = 0;
        this._bestOffer = 0;
    }

    /**
     * Get index of a specific offer in _uniqueOffers based on a given digit.
     * If no offer with the specified digit is present in _uniqueOffers, container size is returned.
     * @param digit value of digit of the searched offer
     * @return  index of _uniqueOffers, containing the searched offer
     */
    private int getUniqueOfferIndex(int digit)
    {
        int index = 0;
        for (TableOffer o : _uniqueOffers)
        {
            if (digit == o._digit)
            {
                break;
            }
            ++index;
        }
        return index;
    }

    /** Custom exception thrown when a Player is about to offer a digit the second time before rejection or withdrawal */
    public static class OverwriteOfferException extends RuntimeException
    {
        final int _playerInternalIndex;
        public OverwriteOfferException(String msg, int playerInternalIndex)
        {
            super(msg);
            this._playerInternalIndex = playerInternalIndex;
        }
    }

    public int getOfferCount() {return _offerCount;}

    public void incrementAcceptanceCount() {++_acceptanceCount;}

    public boolean allAcceptances() {return _acceptanceCount == 3;}

    public void resetAcceptanceCount() {_acceptanceCount = 0;}

    public boolean isDenied(int digit) {return _deniedMask[digit-1];}

    public void setBestOffer(int digit) {_bestOffer = digit;}

    public int getBestOffer() {return  _bestOffer;}

    public void setSpecifyFlag(int n, boolean value) {_specifyFlags[n] = value;}

    public boolean getSpecifyFlag(int n) {return _specifyFlags[n];}

    public boolean allSpecifyFlagTrue() {return _specifyFlags[0] && _specifyFlags[1] && _specifyFlags[2];}

    public void setRequestPending(int n, boolean value) {_requestPending[n] = value;}

    public boolean noRequestsPending() {return _requestPending[0] && _requestPending[1] && _requestPending[2];}

    public void setOffer(int n, int digit, int weight)
    {
        if (_offers[n] != 0)
        {
            throw new OverwriteOfferException("Cannot overwrite offer before rejecting or withdrawing it.", n);
        }
        ++_offerCount;
        _offers[n] = digit;
        final int digitIndex = getUniqueOfferIndex(digit);
        if (digitIndex == _uniqueOffers.size()) // New digit was offered
        {
            _uniqueOffers.add(new TableOffer(digit));
            // Reset flags for other players
            for (int i = 0; i < 3; ++i)
            {
                if (i != n)
                {
                    _specifyFlags[i] = false;
                }
            }
        }
        final TableOffer offerRef = _uniqueOffers.get(digitIndex);
        offerRef._weights[n] = weight;
        offerRef._weightFlags[n] = true;
    }

    /**
     * Set weight for an offer with specified digit for a single Player.
     * If such an offer doesn't exist, the function does nothing.
     * @param n index of Player in internal mapping
     * @param digit digit of the offer
     * @param weight    weight to be set
     */
    public void setWeight(int n, int digit, float weight)
    {
        // NOTE: May be optimized by getting offer reference at the beginning
        final int digitIndex = getUniqueOfferIndex(digit);
        if (digitIndex < _uniqueOffers.size()) // If the offer exists
        {
            final TableOffer offerRef = _uniqueOffers.get(digitIndex);
            offerRef._weights[n] = weight;
            offerRef._weightFlags[n] = true;
        }
    }

    public int[] getUnknownDigits(int n)
    {
        Set<Integer> unknownDigitsSet = new HashSet<>();
        for (TableOffer o : _uniqueOffers)
        {
            if (!o._weightFlags[n])
            {
                unknownDigitsSet.add(o._digit);
            }
        }
        // Note: cannot use unknownDigitsSet.toArray(), because it returns Integer[] instead of int[]
        int[] unknownDigitsArray = new int[unknownDigitsSet.size()];
        int i = 0;
        for (Integer d : unknownDigitsSet)
        {
            unknownDigitsArray[i] = d;
            ++i;
        }
        return unknownDigitsArray;
    }

    /**
     * Withdraw all offers with a digit that causes conflict for some player.
     * If offer with specified digit does not exist, the method does nothing.
     * @param digit value of offer causing conflict
     * @return  list of internal indices of Players that have their offers withdrawn
     */
    public ArrayList<Integer> withdrawDigit(int digit)
    {
        ArrayList<Integer> playerIndices = new ArrayList<>();
        final int digitIndex = getUniqueOfferIndex(digit);
        if (digitIndex < _uniqueOffers.size()) // If the offer exists
        {
            _deniedMask[digit-1] = true;
            _uniqueOffers.remove(digitIndex);
            for (int i = 0; i < 3; ++i)
            {
                if (_offers[i] == digit)
                {
                    _offers[i] = 0;
                    playerIndices.add(i);
                    --_offerCount;
                }
            }
        }
        return playerIndices;
    }

    private float sumWeights(TableOffer o)
    {
        float sum = 0;
        for (int i = 0; i < 3; ++i)
        {
            sum += o._weights[i];
        }
        return sum;
    }

    public void chooseBestOffer()
    {
        final Iterator<TableOffer> offerIter = _uniqueOffers.iterator();
        final ArrayList<Integer> bestDigits = new ArrayList<>();
        TableOffer offerRef = offerIter.next();
        float maxWeightsSum = sumWeights(offerRef);
        while (offerIter.hasNext())
        {
            offerRef = offerIter.next();
            final float tempSum = sumWeights(offerRef);
            if (tempSum > maxWeightsSum)
            {
                maxWeightsSum = tempSum;
                bestDigits.clear();
                bestDigits.add(offerRef._digit);
            }
            else if (tempSum == maxWeightsSum)
            {
                bestDigits.add(offerRef._digit);
            }
        }

        _bestOffer = bestDigits.get(1); // Can be adjusted to get random digit
    }

    public void reset()
    {
        for (int i = 0; i < 3; ++i)
        {
            _offers[i] = 0;
            _specifyFlags[i] = false;
            _requestPending[i] = false;
        }
        _uniqueOffers.clear();
        Arrays.fill(_deniedMask, false);
        _offerCount = 0;
        _acceptanceCount = 0;
        _bestOffer = 0;
    }
}
