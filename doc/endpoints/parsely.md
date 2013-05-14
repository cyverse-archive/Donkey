# Parsely Endpoints

Parsely - http://github.com/iPlantCollaborativeOpenSource/parsely

GET /secured/parsely/triples in Donkey maps to /triples in parsely. See the parsely docs for more info.

GET /secured/parsely/type in Donkey maps to GET /type in parsely. See the parsely docs for more info.

POST /secured/parsely/type in Donkey maps to POST /type in parsely. See the parsely docs for more info.

DELETE /secured/parsely/type in Donkey maps to DELETE /type in parsely. See the parsely docs for more info.

GET /secured/parsely/type-list in Donkey maps to GET /type-list in parsely. See the parsely docs for more info.

GET /secured/parsely/type/paths in Donkey maps to GET /type/paths in parsely. See the parsely docs for more info.

All of the normal Donkey caveats about dealing with secured endpoints apply (i.e. use proxyToken instead of 'user' in the query params).