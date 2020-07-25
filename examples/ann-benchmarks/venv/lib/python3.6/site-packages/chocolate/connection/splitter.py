from ..base import Connection
from ..space import Distribution, Space, _Constant

def split_space(space):
    """Splits a conditional space into several unconditional subspaces.

    Args:
        space: A conditional space.

    Returns:
        A list of all combinations of unconditional subspaces forming the
        input space

    """
    if not isinstance(space, Space):
        space = Space(space)

    splitted = list()
    for subspace in space.subspaces():
        out_subspace = dict()
        for name, v in zip(space.names(), subspace):
            if isinstance(v, Distribution):
                out_subspace[name] = v
            elif v is not None:
                # The condition has to be a distribution to be kept out of subspace keys
                out_subspace[name] = _Constant(v)

        splitted.append(Space(out_subspace))
    return splitted


def transform_suboutput(subout, space):
    """Takes the output of the splitted subspace and transforms it to an output
    valid in the full search space.

    Args:
        subout: The output of the subspace.
        space: The original conditional space.

    Returns:
        A full set of parameters containing the good conditions.

    """
    # Get all subspace keys
    # TODO: Maybe we need a function in space rather than ending with _subspace
    subspace_keys = set(n for n in space.names(unique=False) if n.endswith("_subspace"))
    key2name = dict(zip(space.names(), space.names(unique=False)))

    # Separate condition values from parameter values
    out_subkeys = {}
    out_param = {}
    for k, v in subout.items():
        if k in subspace_keys:
            out_subkeys[k] = v
        else:
            out_param[key2name[k]] = v

    # Construct a temporary vector and transform subspace keys to params
    out = space([out_subkeys[n] if n in out_subkeys else 0 for n in space.names()])

    # Update the output with the parameters real values
    out.update(out_param)
    return out


class ConnectionSplitter(Connection):
    """Splits the data sent to a single connection just like it was in separate tables
    using a split id. One ConnectionSplitter is required by split.
    
    Example:
        To split a connection in 3 separate groups ::
            
            for i in range(3):
                splitted_connection = ConnectionSplitter(orig_connection, i, "_split_id")
        
        Then use the splitted connections in the rest of the algorithm.
    
    Args:
        connection: The original database connection to split.
        id: The unique id of this split.
        split_col: The column name to use as a split.
    """
    def __init__(self, connection, id, split_col):
        self.connection = connection
        self.id = id
        assert split_col, "Split column name must not be empty"
        assert "." not in split_col, "Split column name must not contain any '.'."
        self.split_col = split_col

    def lock(self):
        return self.connection.lock()

    def all_results(self):
        results = self.connection.all_results()
        return [r for r in results if r[self.split_col] == self.id]

    def find_results(self, filter):
        filter = filter.copy()
        filter[self.split_col] = self.id
        return self.connection.find_results(filter)

    def insert_result(self, entry):
        entry = entry.copy()
        entry[self.split_col] = self.id
        return self.connection.insert_result(entry)

    def update_result(self, token, value):
        raise RuntimeError("Cannot update result from splitted connection")

    def count_results(self):
        return len(self.all_results())

    def all_complementary(self):
        complementary = self.connection.all_complementary()
        return [c for c in complementary if c[self.split_col] == self.id]

    def insert_complementary(self, document):
        document = document.copy()
        document[self.split_col] = self.id
        self.connection.insert_complementary(document)

    def find_complementary(self, filter):
        filter = filter.copy()
        filter[self.split_col] = self.id
        return self.connection.find_complementary(filter)

    def get_space(self):
        # Splitter is not responsible of the space
        return None

    def insert_space(self, space):
        # Splitter is not responsible of the space
        pass

    def clear(self):
        # Splitter is not responsible of the space
        pass

    def pop_id(self, document):
        """Pops the database unique id from the row."""
        return self.connection.pop_id(document)
