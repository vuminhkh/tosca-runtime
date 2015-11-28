Test API:

curl -X GET http://0.0.0.0:9000/context

curl -H "Content-Type: application/json" -X POST http://0.0.0.0:9000/context -d '
 {
     "test" : "test",
     "toto": "tata"
 }
'