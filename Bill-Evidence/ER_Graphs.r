# Erdos-Renyi Graph generating.

library(igraph)
library(dpylr)

# 10 to the power of 1, 1.5, 2 etc.

nodes <- c(10, 32 ,100, 316, 1000, 3162, 10000)

eps <- 0.1

for(n in nodes){
  p1 <- (1-eps)*log(n) / n
  p2 <- (1+eps)*log(n) / n
  
  sample_gnp(n, p1, directed = FALSE, loops = FALSE) %>%
    as_edgelist() %>%
    write.csv(file = paste("/Users/billnunn/Desktop/G_", as.character(n), "_p1"))

  sample_gnp(n, p2, directed = FALSE, loops = FALSE) %>%
    as_edgelist() %>%
    write.csv(file = paste("/Users/billnunn/Desktop/G_", as.character(n), "_p2"))
}

rm(n, p1, p2)

