# JsonPathGenerator

This project, using two mocked static json objects, uses the [Jayway Json Path Library](https://github.com/json-path/JsonPath)
to:
- generate a list of json paths representing the differences between those json objects
- create a new json object starting from the initial json object and adding the variations present in the json path list 