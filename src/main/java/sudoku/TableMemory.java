package sudoku;

import akka.actor.typed.javadsl.ActorContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TableMemory
{
    /** Values of digits offered by Players. Zeros indicate currently unused slots. */
    private int[] _offers;
    /** Weights of digits offered by Players. The first index is for Player and the second for digit (order is the same
     * as in _offers). */
    private float[][] _weights;
    /** Sums of _weights for every offered digit. Valid only when all _weightFlags for a given Player are set to true. */
    private float[][] _weightSums;
    /** Flags indicating whether a weight value for a digit is known (for a specific Player). The first index is for
     * Player and the second for digit (order is the same as in _offers).*/
    private boolean[][] _weightFlags;
    /** Mask of denied digits. True means, the digit causes a conflict for some Player. */
    private boolean[] _deniedMask;
    /** Number of offers currently proposed by Players */
    private int _offerCount;
    /** Number of acceptance messages received from Players */
    private int _acceptanceCount;
    /** The offer chosen after negotiations with Players. Zero means, the offer was not chosen yet. */
    private int _bestOffer;

    private TableMemory(int sudokuSize)
    {
        _offers = new int[3]; // By default initialized to 0
        _weights = new float[3][3];
        _weightFlags = new boolean[3][3]; // By default initialized to false
        _deniedMask = new boolean[sudokuSize]; // By default initialized to false
        _offerCount = 0;
        _acceptanceCount = 0;
        _bestOffer = 0;
    }

    public void setOffer(int n, int digit) {_offers[n] = digit;}

    public void setWeight(int n, int m, float weight)
    {
        _weights[n][m] = weight;
        _weightFlags[n][m] = true;
    }

    public int[] getUnknownDigits(int n)
    {
        Set<Integer> unknownDigitsSet = new HashSet<Integer>();
        for (int i = 0; i < 3; ++i)
        {
            final int digit = _offers[i];
            if (digit != 0 && !_weightFlags[n][i]) // No information about a certain digit
            {
                unknownDigitsSet.add(digit);
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

    public void denyDigit(int digit)
    {
        _deniedMask[digit-1] = true;
        for (int n = 0; n < 3; ++n)
        {
            if (_offers[n] == digit)
            {
                _offers[n] = 0;
                for (int m = 0; m < 3; ++m)
                {
                    _weights[n][m] = 0; // Not needed since _weightFlags[n][m] == false indicates, the value is not valid
                    _weightFlags[n][m] = false;
                }
                --_offerCount;
            }
        }
    }

    public void reset()
    {
        for (int i = 0; i < 3; ++i)
        {
            _offers[i] = 0;
            for (int j = 0; j < 3; ++j)
            {
                _weights[i][j] = 0; // Not needed since _weightFlags[i][j] == false indicates, the value is not valid
                _weightFlags[i][j] = false;
            }
        }
        Arrays.fill(_deniedMask, false);
        _offerCount = 0;
        _acceptanceCount = 0;
    }
}
