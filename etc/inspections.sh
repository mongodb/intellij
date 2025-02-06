#!/bin/bash

./etc/plugin-logs.sh |\
 grep 'inspection_id' |\
 jq '{ inspection: .inspection_type, id: .inspection_id, status: .error_status, meta: { error_field_type: .error_field_type, actual_field_type: .actual_field_type } }' |\
 jq -s |\
 jq 'group_by(.id) | map({ id: .[0].id, inspection: .[0].inspection, status: map(.status), meta: map(.meta) })' |
 jq '.[]'

