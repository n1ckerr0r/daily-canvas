package auth

import "testing"

func TestIsValidEmail(t *testing.T) {
	tests := map[string]bool{
		"user@example.com":        true,
		" User@Example.COM ":      true,
		"first.last+tag@site.org": true,
		"":                        false,
		"plain-address":           false,
		"user@localhost":          false,
		"user@@example.com":       false,
		"user..name@example.com":  false,
		"Name <user@example.com>": false,
	}
	for input, want := range tests {
		if got := IsValidEmail(input); got != want {
			t.Errorf("IsValidEmail(%q) = %v, want %v", input, got, want)
		}
	}
}
