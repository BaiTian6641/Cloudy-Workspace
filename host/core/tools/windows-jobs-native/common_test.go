//go:build windows && amd64

package main

import (
	"strings"
	"testing"
)

func validOutput() string {
	s := "=== RUN   TestNativeWindowsOwnedJobs\n"
	for _, name := range expectedTests {
		s += "    --- PASS: " + name + " (0.01s)\n"
	}
	return s + "PASS\n"
}
func TestTerminalParserAcceptsExactCases(t *testing.T) {
	for _, s := range []string{validOutput(), strings.ReplaceAll(validOutput(), "\n", "\r\n")} {
		if err := verifyTerminals([]byte(s)); err != nil {
			t.Fatal(err)
		}
	}
}
func TestTerminalParserRejectsIncompleteOrMisattributedEvidence(t *testing.T) {
	valid := validOutput()
	cases := []string{"", "PASS\n", strings.Replace(valid, "--- PASS:", "--- FAIL:", 1), strings.Replace(valid, "--- PASS:", "--- SKIP:", 1), strings.Replace(valid, expectedTests[0], "Unexpected", 1), valid + "PASS\n", valid + "--- PASS: " + expectedTests[0] + " (0.01s)\n", strings.Replace(valid, "    --- PASS: "+expectedTests[0]+" (0.01s)\n", "", 1), valid + "--- UNKNOWN: Foo\n"}
	for i, s := range cases {
		if verifyTerminals([]byte(s)) == nil {
			t.Fatalf("invalid control %d accepted", i)
		}
	}
}
func TestDigestValidation(t *testing.T) {
	for _, s := range []string{"", strings.Repeat("a", 63), strings.Repeat("G", 64), strings.Repeat("A", 64)} {
		if validDigest(s) {
			t.Fatal("invalid digest accepted")
		}
	}
	if !validDigest(strings.Repeat("a", 64)) {
		t.Fatal("valid digest refused")
	}
}
