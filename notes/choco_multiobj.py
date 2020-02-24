from sklearn.datasets import make_classification
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import precision_score, recall_score
from sklearn.model_selection import train_test_split

import chocolate as choco
import chocolate.mo as mo

import matplotlib.pyplot as plt


def score_gbt(X, y, params):
    print(f"Running with params {params}")
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)
    gbt = GradientBoostingClassifier(**params)
    gbt.fit(X_train, y_train)
    y_pred = gbt.predict(X_test)
    p = -precision_score(y_test, y_pred)
    r = -recall_score(y_test, y_pred)
    print(f"Returning precision = {p}, recall = {r}")
    return p, r

X, y = make_classification(n_samples=25000, random_state=1)
conn = choco.SQLiteConnection(url="sqlite:////tmp/choco_multiobj.db")
s = {"learning_rate": choco.uniform(0.001, 0.1),
     "n_estimators": choco.quantized_uniform(25, 525, 25),
     "max_depth": choco.quantized_uniform(2, 10, 2),
     "subsample": choco.quantized_uniform(0.7, 1.05, 0.05)}

sampler = choco.MOCMAES(conn, s, mu=5)
token, params = sampler.next()
loss = score_gbt(X, y, params)
sampler.update(token, loss)

results = conn.results_as_dataframe()
losses = results[["_loss_0", "_loss_1"]].values
first_front = mo.argsortNondominated(losses, len(losses), first_front_only=True)

plt.scatter(losses[:, 0], losses[:, 1], label="All candidates")
plt.scatter(losses[first_front, 0], losses[first_front, 1], label="Optimal candidates")
plt.xlabel("precision")
plt.ylabel("recall")
plt.legend()
plt.show()
