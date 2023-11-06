from datetime import datetime
import json
import requests
import os

def get_releases(repo_owner, repo_name, github_token):
  url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/releases"
  headers = {"Authorization": f"token {github_token}"}
  response = requests.get(url, headers=headers)
  if response.status_code != 200:
    raise Exception("Failed to get releases: {}".format(response.content))
  releases = json.loads(response.content)
  return releases

# def delete_release(repo_owner, repo_name, release_name, github_token):
#   url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/releases/{release_name}"
#   headers = {"Authorization": f"token {github_token}"}
#   response = requests.delete(url, headers=headers)
#   if response.status_code != 204:
#     raise Exception("Failed to delete release: {}".format(response.content))

def delete_old_pre_releases(repo_owner, repo_name, github_token):
  releases = get_releases(repo_owner, repo_name, github_token)
  now = datetime.now()
  for release in releases:
    published_at = datetime.strptime(release["published_at"], '%Y-%m-%dT%H:%M:%SZ')
    # TODO increase to 7 days after verifying it works.
    if release["prerelease"] and (now - published_at).days >= 0:
      print(f"Deleting {release['name']}")
      # delete_release(repo_owner, repo_name, release["name"], github_token)

if __name__ == "__main__":
  github_token = os.environ["GITHUB_TOKEN"]
  REPO_OWNER = "alexklibisz"
  REPO_NAME = "elastiknn"
  delete_old_pre_releases(REPO_OWNER, REPO_NAME, github_token)
