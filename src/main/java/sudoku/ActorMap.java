package sudoku;

import akka.actor.typed.ActorRef;

import java.util.HashMap;
import java.util.Map;

public class ActorMap<T>
{
    /** Map from global Actor id to internal index */
    private final Map<Integer, Integer> _indices;
    /** Array of Actor references indexed internally */
    private Object[] _actors;
    /** Numer of actors currently registered */
    private int _actorCount;

    public ActorMap(int maxActorCount)
    {
        _indices = new HashMap<>();
        _actors = new Object[maxActorCount];
        _actorCount = 0;
    }

    /**
     * Get actor reference by internal index.
     * @param i internal index
     * @return  actor reference
     */
    public T getActor(int i)
    {
        @SuppressWarnings("unchecked")
        final T a = (T) _actors[i];
        return a;
    }

    /**
     * Get actor reference by global id.
     * @param id    actor global id
     * @return  actor reference
     */
    public T getActorById(int id)
    {
        return getActor(getIndex(id));
    }

    /**
     * Get internal index by global id.
     * @param id    actor global id
     * @return  internal index
     */
    public int getIndex(int id)
    {
        return _indices.get(id);
    }

    /**
     * Register new actor in the map.
     * Does not perform a check for registering more actors than it should.
     * @param globalId  registered actor's global id
     * @param actorRef  reference to the registered actor
     */
    public void register(int globalId, T actorRef)
    {
        _indices.put(globalId, _actorCount);
        _actors[_actorCount] = actorRef;
        ++_actorCount;
    }

    /**
     * Check whether more actors can be registered.
     * @return  true, if cannot register more actors
     */
    public boolean isFull()
    {
        return _actorCount == _actors.length;
    }
}
