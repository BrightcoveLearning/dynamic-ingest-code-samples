#!/usr/bin/env python

import sys
import requests
import json
import argparse

pub_id = "***ACCOUNT ID HERE****"
client_id = "***CLIENT ID HERE****"
client_secret = "***CLIENT SECRET HERE****"
access_token_url = "https://oauth.brightcove.com/v3/access_token"
profiles_base_url = "http://ingestion.api.brightcove.com/v1/accounts/{pubid}/profiles"

# Making requests with the Brightcove CMS API requires the use of OAuth
# get_authorization_headers is a convenience method that obtains an OAuth access token
# and embeds it appropriately in a map suitable for use with the requests HTTP library
def get_authorization_headers():
    access_token = None
    r = requests.post(access_token_url, params="grant_type=client_credentials", auth=(client_id, client_secret), verify=False)
    if r.status_code == 200:
        access_token = r.json().get('access_token')
        print(access_token)
    return { 'Authorization': 'Bearer ' + access_token, "Content-Type": "application/json" }

# create_video makes the CMS API call to create a video in the VideoCloud catalog
# This example demonstrates setting only the 'name' attribute on the created title
def create_video():
    url = ("https://cms.api.brightcove.com/v1/accounts/{pubid}/videos/").format(pubid=pub_id)
    data = '{"name": "***VIDEO TITLE HERE***"}'
    r = requests.post(url, headers=get_authorization_headers(), data=data)
    return r.json()

# di_request makes the Ingest API call to populate a video with transcoded renditions
# from a remotely accessible source asset.
def di_request(video_id):
    url = ("https://ingest.api.brightcove.com/v1/accounts/{pubid}/videos/{videoid}/ingest-requests").format(pubid=pub_id, videoid=video_id)
    data = '''{"master": { "url": "****SOURCE VIDEO URL HERE***" }}'''
    r = requests.post(url, headers=get_authorization_headers(), data=data)
    return r.json()

if __name__ == '__main__':
    v = create_video()
    print di_request(v['id'])