package com.mongodb.jbplugin.inspections

data object AIPrompts {
    fun suggestProjectionPrompt(
        query: String,
        queryContext: String,
        sampleDocuments: String,
    ) = """
Assume that you are an architect well versed in MongoDB schema design and query optimization.
You will be given:
1. a code snippet, written using MongoDB Java Driver, containing code that queries MongoDB collection, marked in comments as - // TARGET QUERY
2. surrounding code with the target query itself, marked in comments as - // SURROUNDING CODE
3. some sample documents from the collection that is being queried, marked in comments as - // SAMPLE DOCUMENTS

Your task, when prompted with "SUGGEST PROJECTION", will be to:
1. Analyze the query to see if a projection is used or not. Output of this step should be list of projected fields or empty list.
2. Analyze the query to see if a limit is used or not. Output of this step should be the limit applied to the query or null.
3. Analyze the query to see if its streaming the query return values using a cursor or buffering them in memory using some List or Object. Output of this step should be either "CURSOR" OR "BUFFERRED IN MEMORY".
4. Analyze the surrounding code to see how the query results are used and particularly what fields from the query results are used. Output of this step should be a list of fields that are used in the surrounding context or empty list.
5. Generate a schema using the sample documents provided. The schema should contain the field names as key and the value should be an object having the possible types, probability of field to have that type of value, distribution of values for each field to determine which values is what percentage of the sample size. For nested fields, flatten them all on root level using "." notation to combine with the parent fields.
6. Analyze the schema to calculate the probability of the query ending up returning huge chunk of data. A query can return a huge chunk of data if there are fields that are unbounded, for example:
  - a or multiple field/s that nests a list of subdocuments / strings / numbers
  - there are too many fields and some of the fields have values with lots of characters
  Output of this step should be: High, Moderate, Low
7. Use sample documents provided and the output of step 2 (the limit) to address the possible deviation in the probability calculated in step 5.
8. Use the following algorithm to suggest a projection:
  - Whenever suggesting a projection, consider the fields that are used in the context to target those specific fields. If there are no fields used then simply suggest a message around the lines of:
    - Query has <x> probablity of blowing up the process memory so consider using a projection
  
  - If there is a projection then suggest an optimisation to existing projection only if:
    - there is no limit applied
    - if the applied limit is high and output of step 3 is "BUFFERRED IN MEMORY"
    - if any of the above and the projected fields are more than the fields used in context
  
  - If there is no projection then suggest a projection:
    - If the output of step 3 is "BUFFERRED IN MEMORY"
    - If there is no limit applied
    - If the applied limit is high and output of step 3 is "BUFFERRED IN MEMORY"

9. Final and the only output should be a json object containing the following fields:
  - suggestion: One of:
    - PROJECTION_RECOMMENDED: If there is no projection and we are suggesting one
    - PROJECTION_OPTIMISATION_RECOMMENDED: If there is a projection and we are suggesting an optimisation
    - null: If there is no suggestion
  - suggestionMessage(String | null): A super small message about the probability of the query to return large chunk of documents that might blow up process's memory and thus the projection suggestion.
  - suggestedProjection(List<String>): List of suggested fields to be projected. If we know for sure what fields needs to be projected and if those are not already projected, then we add those fields in a list here otherwise its an empty list.
  - suggestedProjectionMessage(String | null):
    - When there are no fields suggested for projection then a generic message that we could not find suggestion for projection but the query should use a projection / might benefit from projection(based on the value of "suggestion" field)
    - When there are fields suggested for projection then a small message explaining why the fields are suggested for projection.
    - null if there is no suggestion

// TARGET QUERY
$query

// SURROUNDING CODE
$queryContext

// SAMPLE DOCUMENTS
$sampleDocuments

Output only the JSON from step 9 and nothing else.
    """
}
