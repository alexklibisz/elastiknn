from urllib.request import urlopen
import json, sys

# Read target command as arg.
cmd = sys.argv[1]

# Read context from stdin.
ctx = json.loads(sys.stdin.read())

# Context provides the URL where comments get retrieved.
comments_url = ctx['event']['pull_request']['_links']['comments']['href']

# Retrieve comments from this specific PR.
with urlopen(comments_url) as res:
    comments = json.loads(res.read().decode())

# Filter for the ones that come from a project owner and contain the given command.
matching = [
    x for x in comments if x["author_association"] == "OWNER" and cmd in x["body"]
]

# Exit with 0 if found, otherwise 1.
print(len(matching))