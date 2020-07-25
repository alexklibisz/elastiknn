
from contextlib import contextmanager
import pickle
import time

try:
    from pymongo import MongoClient
except ImportError:
    MongoClient = False

from ..base import Connection


class MongoDBConnection(Connection):
    """Connection to a MongoDB database.

    Args:
        url (str): Full url to the database including credentials but omitting the
            database and the collection. When using authenticated databases, the url must
            contain the database and match the ``database`` argument.
        database (str): The database name in the MongoDB engine.
        result_col (str): Collection used to store the experiences and their
            results.
        complementary_col (str): Collection used to store complementary information
            necessary to the optimizer.
        space_table (str): Collection used to save the optimization :class:`Space`.
    """
    def __init__(self, url, database="chocolate", result_col="results", complementary_col="complementary", space_col="space"):
        self.client = MongoClient(url)
        self.db = self.client[database]
        self.result_collection_name = result_col
        self.complementary_collection_name = complementary_col
        self.space_collection_name = space_col
        self.results = self.db[self.result_collection_name]
        self.complementary = self.db[self.complementary_collection_name]
        self.space = self.db[self.space_collection_name]
        self._lock = self.db.lock
        self.hold_lock = False

    @contextmanager
    def lock(self, timeout=-1, poll_interval=0.05):
        """Context manager that locks the entire database.
        
        ::
        
            conn = MongoDBConnection("mongodb://localhost:27017/")
            with conn.lock(timeout=5):
                # The database is lock
                all_ = conn.all_results()
                conn.insert({"new_data" : len(all_)})

            # The database is unlocked

        Args:
            timeout: If the lock could not be acquired in *timeout* seconds
                raises a timeout error. If 0 or less, wait forever.
            poll_interval: Number of seconds between lock acquisition tryouts.

        Raises:
            TimeoutError: Raised if the lock could not be acquired.
        """
        if self.hold_lock:
            yield
        else:
            start_time = time.time()
            l = self._lock.find_one_and_update({"name" : "lock"},
                                               {"$set" : {"lock" : True}},
                                               upsert=True)

            while l is not None and l["lock"] != False and timeout != 0:
                time.sleep(poll_interval)
                l = self._lock.find_one_and_update({"name" : "lock"},
                                                   {"$set" : {"lock" : True}},
                                                   upsert=True)

                if time.time() - start_time > timeout:
                    break

            if l is None or l["lock"] == False:
                # The lock is acquired
                try:
                    self.hold_lock = True
                    yield
                finally:
                    l = self._lock.find_one_and_update({"name" : "lock"},
                                                       {"$set" : {"lock" : False}})
                    self.hold_lock = False
            else:
                raise TimeoutError("Could not acquire MongoDB lock")

    def all_results(self):
        """Get all entries of the result table as a list. The order is
        undefined.
        """
        return list(self.results.find())

    def find_results(self, filter):
        """Get a list of all results associated with *filter*. The order is
        undefined.
        """
        return list(self.results.find(filter))

    def insert_result(self, document):
        """Insert a new *document* in the result table."""
        return self.results.insert_one(document.copy())

    def update_result(self, token, values):
        """Update or add *values* to given documents in the result table.

        Args:
            token: An identifier of the documents to update.
            value: A mapping of values to update or add.
        """
        return self.results.update_many(token, {"$set" : values})

    def count_results(self):
        """Get the total number of entries in the result table."""
        return self.results.count()

    def all_complementary(self):
        """Get all entries of the complementary information table as a list.
        The order is undefined.
        """
        return list(self.complementary.find())

    def insert_complementary(self, document):
        """Insert a new document in the complementary information table."""
        return self.complementary.insert_one(document.copy())

    def find_complementary(self, filter):
        """Find a document from the complementary information table."""
        return self.complementary.find_one(filter)

    def get_space(self):
        """Returns the space used for previous experiments.

        Raises:
            AssertionError: If there are more than one space in the database.
        """
        entry_count = self.space.count()
        if entry_count == 0:
            return None

        assert entry_count == 1, ("Space table unexpectedly contains more than one space.")
        return pickle.loads(self.space.find_one()["space"])

    def insert_space(self, space):
        """Insert a space in the database.

        Raises:
            AssertionError: If a space is already present in the database.
        """
        assert self.space.count() == 0, ("Space table cannot contain more than one space, "
            "clear table first.")
        return self.space.insert_one({"space" : pickle.dumps(space)})

    def clear(self):
        """Clear all data from the database."""
        self.results.delete_many({})
        self.complementary.delete_many({})
        self.space.delete_many({})

    def pop_id(self, document):
        """Pops the database unique id from the document."""
        document.pop("_id", None)
        return document


class _MongoDBConnectionFailedImport(MongoDBConnection):
    def __init__(self, *args, **kwargs):
        raise ImportError("No module named 'pymongo' required for MongoDBConnection.")


if not MongoClient:
    MongoDBConnection = _MongoDBConnectionFailedImport
