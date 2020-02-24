import chocolate as choco


def himmelblau(x, y):
    return (x**2 + y - 11)**2 + (x + y**2 - 7)**2

space = {
    "x": choco.uniform(-6, 6),
    "y": choco.uniform(-6, 6)
}


conn = choco.SQLiteConnection("sqlite:////tmp/choco_basic.db")
sampler = choco.QuasiRandom(conn, space, skip=0)

token, params = sampler.next()

for _ in range(40):
    loss = himmelblau(**params)
    print(loss)
    sampler.update(token, loss)
    token, params = sampler.next()
