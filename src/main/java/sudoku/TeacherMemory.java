package sudoku;

import java.util.HashSet;
import java.util.Set;

public class TeacherMemory
{
    final private int _maxPlayerCount;

    final private int _maxTableCount;

    private int _playerResetCount;

    private int _tableResetCount;

    final private Set<Integer> _tablesNotFinished;

    final private Set<Integer> _normalTables;

    public TeacherMemory(int playerCount, int tableCount, HashSet<Integer> normalTables)
    {
        this._maxPlayerCount = playerCount;
        this._maxTableCount = tableCount;
        this._playerResetCount = 0;
        this._tableResetCount = 0;
        this._tablesNotFinished = new HashSet<>();
        _tablesNotFinished.addAll(normalTables);
        this._normalTables = new HashSet<>();
        _normalTables.addAll(normalTables);
    }

    public void setNormalTables(HashSet<Integer> normalTables)
    {
        _normalTables.clear();
        _normalTables.addAll(normalTables);
    }

    private boolean allResetsCollected()
    {
        return _playerResetCount == _maxPlayerCount && _tableResetCount == _maxTableCount;
    }

    public boolean addPlayerReset()
    {
        ++_playerResetCount;
        return allResetsCollected();
    }

    public boolean addTableReset()
    {
        ++_tableResetCount;
        return allResetsCollected();
    }

    public int addTableFinished(int tableId)
    {
        if(!_tablesNotFinished.remove(tableId)) // if table is not present in the NotFinished Set...
            throw new RuntimeException("Table finished second time!");

        return _tablesNotFinished.size();
    }

    public int[] getTablesNotFinished()
    {
        final int[] notFinishedTableIds = new int[_tablesNotFinished.size()];
        int i = 0;
        for(int notFinishedTableId : _tablesNotFinished)
            notFinishedTableIds[i++] = notFinishedTableId;

        return notFinishedTableIds;
    }

    void reset()
    {
        _playerResetCount = 0;
        _tableResetCount = 0;
        _tablesNotFinished.clear();
        _tablesNotFinished.addAll(_normalTables);
    }
}
