#!/usr/bin/env python
# Notes
# 1. This script runs on Python 2.x - it will not run on Python 3.x without modifications
# 2. The script assumes that the video source file will be in the same directory as the script
#    To upload videos from a different directory, you will need to modify the script
import sys
import requests
import json
import argparse
import boto3

pub_id = "***ACCOUNT ID HERE****"
client_id = "***CLIENT ID HERE****"
client_secret = "***CLIENT SECRET HERE****"
source_filename = "*** LOCAL VIDEO FILE HERE***"
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

# get_upload_location_and_upload_file first performs an authenticated request to discover
# a Brightcove-provided location to securely upload a source file
def get_upload_location_and_upload_file(account_id, video_id, source_filename):
    
    # Perform an authorized request to obtain a file upload location
    url = ("https://cms.api.brightcove.com/v1/accounts/{pubid}/videos/{videoid}/upload-urls/{sourcefilename}").format(pubid=pub_id, videoid=video_id, sourcefilename=source_filename)
    r = requests.get(url, headers=get_authorization_headers())
    upload_urls_response = r.json()
    
    # Upload the contents of our local file to the location provided us
    # This example uses the boto3 library to perform a multipart upload
    # This is the recommended method for uploading large source files
    s3 = boto3.resource('s3',
        aws_access_key_id=upload_urls_response['access_key_id'],
        aws_secret_access_key=upload_urls_response['secret_access_key'],
        aws_session_token=upload_urls_response['session_token'])

    s3.Object(upload_urls_response['bucket'], upload_urls_response['object_key']).upload_file(source_filename)

    return upload_urls_response

# di_request makes the Ingest API call to populate a video with transcoded renditions
# from the source file that was uploaded in the previous step
def di_request(video_id, upload_urls_response):
    url = ("https://ingest.api.brightcove.com/v1/accounts/{pubid}/videos/{videoid}/ingest-requests").format(pubid=pub_id, videoid=video_id)
    data = '''{"master": { "url": "''' + upload_urls_response['api_request_url'] + '''" }}'''
    r = requests.post(url, headers=get_authorization_headers(), data=data)
    return r.json()

if __name__ == '__main__':
    v = create_video()
    upload_urls = get_upload_location_and_upload_file(pub_id, v['id'], source_filename)
    print(di_request(v['id'], upload_urls))
