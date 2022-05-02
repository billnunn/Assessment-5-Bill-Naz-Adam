
library(igraph)
library(dplyr)
library(microbenchmark)

path <- "/Users/billnunn/Desktop/Data"

nodes <- c(32, 100, 316, 1000, 3162, 10000)

el <- paste(path, "/G_", as.character(10), "_p1.csv", sep = "") %>% 
     read.csv()
el <- el[,2:3]
G <- graph_from_data_frame(el)

p1_comp <- microbenchmark("Components" = {components(G)})
p1_comp <- summary(p1_comp)

p1_pr <- microbenchmark("Page Rank" = {page_rank(G, damping=0.85)})
p1_pr <- summary(p1_pr)

rm(el, G)

for(n in nodes){
  el <- paste(path, "/G_", as.character(n), "_p1.csv", sep = "") %>% 
    read.csv()
  el <- el[,2:3]
  G <- graph_from_data_frame(el)
  
  p1_comp <- rbind(p1_comp, summary(
                   microbenchmark("Components" = {components(G)})))
  
  p1_pr <- rbind(p1_pr, summary(
                 microbenchmark("Page Rank" = {page_rank(G, damping=0.85)})))
}
rm(G, el)

p1_comp
p1_pr

# Now for the p2 graphs

el <- paste(path, "/G_", as.character(10), "_p2.csv", sep = "") %>% 
  read.csv()
el <- el[,2:3]
G <- graph_from_data_frame(el)

p2_comp <- microbenchmark("Components" = {components(G)})
p2_comp <- summary(p2_comp)

p2_pr <- microbenchmark("Page Rank" = {page_rank(G, damping=0.85)})
p2_pr <- summary(p2_pr)

rm(G, el)

for(n in nodes){
  el <- paste(path, "/G_", as.character(n), "_p2.csv", sep = "") %>% 
    read.csv()
  el <- el[,2:3]
  G <- graph_from_data_frame(el)
  
  p2_comp <- rbind(p2_comp, summary(
                   microbenchmark("Components" = {components(G)})))
  
  p2_pr <- rbind(p2_pr, summary(
                 microbenchmark("Page Rank" = {page_rank(G, damping=0.85)})))
}
rm(G, el, n)

p2_comp
p2_pr

rm(nodes)

# Great that's worked.

# Get rid of the unnecessary cols.

p1_comp <- p1_comp[,2:7]
p1_pr <- p1_pr[,2:7]
p2_comp <- p2_comp[,2:7]
p2_pr <- p2_pr[,2:7]

# Make sure the later computations are in miliseconds not seconds!

p1_comp[6:7,] <- p1_comp[6:7,] * 1000
p1_comp
p1_pr[5:7,] <- p1_pr[5:7,] * 1000
p1_pr
p2_comp[6:7,] <- p2_comp[6:7,] * 1000
p2_comp
p2_pr[5:7,] <- p2_pr[5:7,] * 1000
p2_pr

write.csv(p1_comp, "/Users/billnunn/Desktop/Data/p1_comp.csv")
write.csv(p1_pr, "/Users/billnunn/Desktop/Data/p1_pr.csv")
write.csv(p2_comp, "/Users/billnunn/Desktop/Data/p2_comp.csv")
write.csv(p2_pr, "/Users/billnunn/Desktop/Data/p2_pr.csv")

