library(dplyr)
library(tidyr)
library(data.table)
library(stringr)

LDAP1 <- fread("/Users/billnunn/Desktop/r3.1/LDAP/2009-12.csv")
LDAP2 <- fread("/Users/billnunn/Desktop/r3.1/LDAP/2010-01.csv")
LDAP3 <- fread("/Users/billnunn/Desktop/r3.1/LDAP/2010-02.csv")

# We see that no employees were fired in the first month and 7
# were fired in the second month:
setdiff(LDAP1$user_id, LDAP2$user_id)
fired <- setdiff(LDAP2$user_id, LDAP3$user_id)

# We now check when each of these employees last logged on.
logons <- read.csv("/Users/billnunn/Desktop/r3.1/logon.csv")

for(id in fired){
  tail(logons[logons$user == id,]) %>% print()
}
rm(id)

rm(LDAP2, LDAP3, logons)

# Great, all these guy's last days were indeed in the second month.
# We therefore have one month of email data where everyone's present.

# We read in this first month of email data, we use `fread` to avoid
# reading in the (nonsense) `content` column.

emails <- fread("/Users/billnunn/Desktop/r3.1/email.csv", nrows = 129559,
              select = c(1:6))

# For now we'll just consider the `to` and `from` columns of our data.

tofrom <- emails[,3:4]
head(tofrom)

# We see that emails can be sent to multiple recipients and add a
# column to count them.

tofrom[, recipients := str_count(to, ";") + 1]
head(tofrom)

# We see that we have at most 5 recipients per email
max(tofrom[,3])

tofrom <- tofrom[,1:2]

# We now use a tidyr function to split the multiple recipients into
# seperate rows (I'd originally coded my own version of this  function
# but it didn't work as well as tidyr's)

tofrom <- separate_rows(tofrom, to, sep = ";")
head(tofrom)

# edges <- tofrom %>% unique

edges <- tofrom %>% count(to, from)
head(edges)

write.csv(edges, "/Users/billnunn/Desktop/edges.csv", row.names = FALSE)

test <- read.csv("/Users/billnunn/Desktop/edges.csv")
head(test)

#################################################

edges <- edges[,1:2]
head(edges)

library(igraph)
library(Rtsne)

G <- graph_from_data_frame(edges, directed = FALSE, 
                                 vertices = NULL)

length(V(G))

G_embed <- embed_laplacian_matrix(G, 10, options = list(maxiter=2000))

set.seed(23)
G_tsne <- Rtsne(G_embed$X, dims = 2, initial_dims = 10)
plot(G_tsne$Y, pch=19, cex=0.2)

plot_cols <- c()

for(email in V(G)$name){
  if(email %in% LDAP1$email){
    plot_cols <- c(plot_cols, 
                   LDAP1$department[match(email, LDAP1$email)])
  }
  else{
    plot_cols <- c(plot_cols, "External")
  }
}

