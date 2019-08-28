package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"os"
)

type ParseRequest struct {
	Content string `json:"content"`
}

func main() {
	dat, err := ioutil.ReadFile(os.Args[1])
	if err != nil {
		panic(err)
	}
	dat, err = json.Marshal(ParseRequest{string(dat)})
	if err != nil {
		panic(err)
	}

	fmt.Println(string(dat))
}
