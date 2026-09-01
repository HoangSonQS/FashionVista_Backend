package com.fashionvista.backend.integration.sapo.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SapoNameSplitterTest {

    @Test
    void splitLastName_TwoWordName_SplitsFirstAndLast() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Nguyen Anh");

        assertThat(split.getFirstName()).isEqualTo("Nguyen");
        assertThat(split.getLastName()).isEqualTo("Anh");
    }

    @Test
    void splitLastName_MultiWordName_SplitsOnLastSpaceOnly() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Nguyen Van Anh");

        assertThat(split.getFirstName()).isEqualTo("Nguyen Van");
        assertThat(split.getLastName()).isEqualTo("Anh");
    }

    @Test
    void splitLastName_SingleWordName_LastNameIsEmpty() {
        SapoNameSplitter.Split split = SapoNameSplitter.splitLastName("Madonna");

        assertThat(split.getFirstName()).isEqualTo("Madonna");
        assertThat(split.getLastName()).isEqualTo("");
    }
}
